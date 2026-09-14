package com.grw.xiaobai.hybrid.llm.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.collect.Sets;
import com.grw.xiaobai.hybrid.llm.utils.converter.OrikaBeanConverter;
import com.grw.xiaobai.hybrid.llm.annotation.UserPermission;
import com.grw.xiaobai.hybrid.llm.config.ApplicationConfig;
import com.grw.xiaobai.hybrid.llm.constant.ErrorCodeConstants;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.entity.user.BlockedNotification;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.enums.AccountStatusEnum;
import com.grw.xiaobai.hybrid.llm.enums.LoginStatusEnum;
import com.grw.xiaobai.hybrid.llm.enums.ReturnCodeEnum;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.exception.BusinessLogicException;
import com.grw.xiaobai.hybrid.llm.manager.UserContextManager;
import com.grw.xiaobai.hybrid.llm.service.ChatSessionService;
import com.grw.xiaobai.hybrid.llm.service.CoreUserService;
import com.grw.xiaobai.hybrid.llm.service.SessionService;
import com.grw.xiaobai.hybrid.llm.service.UserService;
import com.grw.xiaobai.hybrid.llm.utils.AspectUtil;
import com.grw.xiaobai.hybrid.llm.utils.PasswordEncryptor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    private static final String USER_FILE_PATH = FilePathConstants.USER_JSON_PATH;
    private static final String NOTIFICATION_FILE_PATH = FilePathConstants.NOTIFICATION_JSON_PATH;
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9]{8,20}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{2,31}$");
    private static final int NICKNAME_MAX_LENGTH = 8;

    private final Map<String, UserInfo> users = new ConcurrentHashMap<>();
    private final Map<String, BlockedNotification> blockedNotifications = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private final List<UserDeleteListener> deleteListeners = new ArrayList<>();

    public void addDeleteListener(UserDeleteListener listener) {
        deleteListeners.add(listener);
    }

    @Resource
    private PasswordEncryptor passwordEncryptor;
    @Resource
    private SessionService sessionService;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private ApplicationConfig applicationConfig;

    @Resource
    private CoreUserService coreUserService;

    @Resource
    private UserContextManager userContextManager;

    @Override
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        UserPermission annotation = AspectUtil.getMethodOrTypeAnnotation(joinPoint, UserPermission.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        UserRoleEnum[] requiredRoles = annotation.value();
        if (requiredRoles.length == 0) {
            return joinPoint.proceed();
        }

        UserInfo currentUser = coreUserService.getCurrentUser();
        UserRoleEnum userRole = currentUser.getRole();

        Set<UserRoleEnum> roleSet = Sets.newHashSet(requiredRoles);
        if (userRole == null || !roleSet.contains(userRole)) {
            return ResultModel.fail(ErrorCodeConstants.FORBIDDEN, "user.permission_denied", "Insufficient permissions");
        }

        return joinPoint.proceed();
    }


    @Override
    public void init() {
        loadFromFile();
        loadNotificationsFromFile();
        if (users.isEmpty()) {
            createDefaultAdmin();
        }
        initialized = true;
    }

    private void createDefaultAdmin() {
        UserInfo admin = new UserInfo();
        admin.setUsername(UserRoleEnum.admin.name());
        admin.setPassword(passwordEncryptor.encrypt(UserRoleEnum.admin.name()));
        admin.setNickname(UserRoleEnum.admin.getDesc());
        admin.setRole(UserRoleEnum.admin);
        admin.setRegisterTime(System.currentTimeMillis());
        admin.setForceChangePassword(true);
        admin.setLastLoginTime(0L);
        admin.setStatus(AccountStatusEnum.normal);
        admin.setRegisterEnd(null);
        users.put(admin.getUsername(), admin);
        saveToFile();
        log.info("Default admin user created");
    }

    public List<UserInfo> getUserList() {
        List<UserInfo> userList = new ArrayList<>(users.values());
        for (UserInfo user : userList) {
            user.setLoginStatus(sessionService.isUserOnline(user.getUsername())
                    ? LoginStatusEnum.online : LoginStatusEnum.offline);
        }
        return userList;
    }

    public void createUser(UserInfo user) {
        validateUsername(user.getUsername());

        if (users.containsKey(user.getUsername())) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.username_exists")
                    .msg("Username already exists")
                    .build();
        }

        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.password_required")
                    .msg("Password cannot be empty")
                    .build();
        }

        if (!validatePassword(user.getPassword())) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.password_invalid")
                    .msg("Password must be 8-20 chars with letters and numbers")
                    .build();
        }

        if (user.getNickname() != null && !user.getNickname().isEmpty() && user.getNickname().length() > NICKNAME_MAX_LENGTH) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.nickname_too_long")
                    .msg("Nickname cannot exceed " + NICKNAME_MAX_LENGTH + " characters")
                    .build();
        }

        UserInfo newUser = OrikaBeanConverter.convert(UserInfo.class, user);
        newUser.setConfirmPassword(null);
        newUser.setPassword(passwordEncryptor.encrypt(user.getPassword()));
        newUser.setNickname(user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername());
        newUser.setRole(user.getRole() != null ? user.getRole() : UserRoleEnum.guest);
        newUser.setRegisterTime(System.currentTimeMillis());
        newUser.setForceChangePassword(false);
        newUser.setLastLoginTime(0L);
        newUser.setStatus(user.getStatus() != null ? user.getStatus() : AccountStatusEnum.normal);
        if (user.getRegisterEnd() != null) {
            newUser.setRegisterEnd(user.getRegisterEnd());
        } else if (UserRoleEnum.guest.equals(newUser.getRole())) {
            newUser.setRegisterEnd(System.currentTimeMillis() + applicationConfig.getUserConfig().getGuestRegisterDurationS() * 1000);
        } else if (UserRoleEnum.user.equals(newUser.getRole())) {
            newUser.setRegisterEnd(System.currentTimeMillis() + applicationConfig.getUserConfig().getUserRegisterDurationS() * 1000);
        } else {
            newUser.setRegisterEnd(null);
        }
        synchronized (this) {
            if (users.containsKey(user.getUsername())) {
                throw BusinessLogicException.builder()
                        .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                        .msgKey("user.username_exists")
                        .msg("Username already exists")
                        .build();
            }
            users.put(user.getUsername(), newUser);
            saveToFile();
        }
    }

    public void registerGuest(UserInfo user) {
        if (!applicationConfig.getUserConfig().isGuestRegisterEnabled()) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.guest_register_closed")
                    .msg("Guest registration is currently unavailable")
                    .build();
        }

        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.username_required")
                    .msg("Username cannot be empty")
                    .build();
        }

        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.password_required")
                    .msg("Password cannot be empty")
                    .build();
        }

        if (!user.getPassword().equals(user.getConfirmPassword())) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.password_mismatch")
                    .msg("Passwords do not match")
                    .build();
        }

        if (user.getDeviceFingerprint() == null || !user.getDeviceFingerprint().matches("^[0-9a-fA-F]{32}$")) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.register_info_invalid")
                    .msg("Registration information is incorrect")
                    .build();
        }
        synchronized (this) {
            for (UserInfo existing : users.values()) {
                if (existing.getDeviceFingerprint() != null
                        && existing.getDeviceFingerprint().equalsIgnoreCase(user.getDeviceFingerprint())) {
                    throw BusinessLogicException.builder()
                            .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                            .msgKey("user.duplicate_registration")
                            .msg("Duplicate registration detected")
                            .build();
                }
            }

            user.setNickname(user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername());
            user.setRole(UserRoleEnum.guest);
            createUser(user);
        }
    }

    public void updateUser(UserInfo user) {
        UserInfo currentUser = getCurrentUser();
        if (currentUser == null) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_logged_in")
                    .msg("Current user is not logged in")
                    .build();
        }

        UserInfo existing = users.get(currentUser.getUsername());
        if (existing == null) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_found")
                    .msg("User does not exist")
                    .build();
        }

        innerUpdate(user, existing);
        users.put(existing.getUsername(), existing);
        saveToFile();
    }

    private void innerUpdate(UserInfo updateUser, UserInfo existing) {
        if (updateUser.getNickname() != null && !updateUser.getNickname().isEmpty()) {
            if (updateUser.getNickname().length() > NICKNAME_MAX_LENGTH) {
                throw BusinessLogicException.builder()
                        .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                        .msgKey("user.nickname_too_long")
                        .msg("Nickname cannot exceed " + NICKNAME_MAX_LENGTH + " characters")
                        .build();
            }
            existing.setNickname(updateUser.getNickname());
        }

        if (updateUser.getRole() != null) {
            existing.setRole(updateUser.getRole());
        }

        if (StringUtils.isNotBlank(updateUser.getPassword())) {
            if (!validatePassword(updateUser.getPassword())) {
                throw BusinessLogicException.builder()
                        .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                        .msgKey("user.password_invalid")
                        .msg("Password must be 8-20 chars with letters and numbers")
                        .build();
            }
            existing.setPassword(passwordEncryptor.encrypt(updateUser.getPassword()));
        }

        if (updateUser.getStatus() != null) {
            existing.setStatus(updateUser.getStatus());
        }

        if (updateUser.getRegisterEnd() != null) {
            existing.setRegisterEnd(updateUser.getRegisterEnd());
        }
    }

    /**
     * need admin role
     *
     * @param username
     * @param user
     */
    public void updateUserByUsername(String username, UserInfo user) {
        validateUsername(username);
        UserInfo currentUser = getCurrentUser();
        if (!UserRoleEnum.admin.equals(currentUser.getRole())) {
            throw BusinessLogicException.builder().msgKey("user.permission_denied").msg("Insufficient permissions").build();
        }
        UserInfo targetUser = users.get(username);
        if (targetUser == null) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_found")
                    .msg("User does not exist")
                    .build();
        }
        if (UserRoleEnum.admin.equals(targetUser.getRole()) && !currentUser.getUsername().equals(username)) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.admin_modify_restricted")
                    .msg("Can only modify own info or non-admin users")
                    .build();
        }

        innerUpdate(user, targetUser);
        users.put(username, targetUser);
        saveToFile();

    }

    public void updateSelf(UserInfo updateUser) {
        UserInfo existingUser = getCurrentUser();
        innerUpdate(updateUser, existingUser);
        users.put(existingUser.getUsername(), existingUser);
        saveToFile();
    }

    public void deleteUser(String username) {
        UserInfo currentUser = getCurrentUser();
        if (currentUser == null) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_logged_in")
                    .msg("Current user is not logged in")
                    .build();
        }

        if (UserRoleEnum.admin.equals(currentUser.getRole())) {
            UserInfo user = users.get(username);
            if (user == null) {
                throw BusinessLogicException.builder()
                        .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                        .msgKey("user.not_found")
                        .msg("User does not exist")
                        .build();
            }

            if (UserRoleEnum.admin.equals(user.getRole())) {
                throw BusinessLogicException.builder()
                        .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                        .msgKey("user.admin_delete_forbidden")
                        .msg("Admin users cannot be deleted")
                        .build();
            }
            chatSessionService.deleteAllSessionsByUsername(username);
            users.remove(username);
            userContextManager.deleteUserContext(username);
            saveToFile();
            for (UserDeleteListener listener : deleteListeners) {
                listener.onUserDelete(username);
            }
        } else {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.delete_admin_only")
                    .msg("Only admins can delete users")
                    .build();
        }
    }


    public String getPassword(String username) {
        UserInfo user = users.get(username);
        if (user == null) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_found")
                    .msg("User does not exist")
                    .build();
        }
        if (user.getRole() == UserRoleEnum.admin && !user.getUsername().equals(getCurrentUser().getUsername())) {
            throw BusinessLogicException.builder().msgKey("user.no_permission").msg("No permission").build();
        }
        return passwordEncryptor.decrypt(user.getPassword());
    }

    public void changePassword(String username, String newPassword, String confirmPassword) {
        UserInfo user = users.get(username);
        if (user == null) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_found")
                    .msg("User does not exist")
                    .build();
        }

        if (!newPassword.equals(confirmPassword)) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.password_mismatch")
                    .msg("Passwords do not match")
                    .build();
        }

        if (!validatePassword(newPassword)) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.password_invalid")
                    .msg("Password must be 8-20 chars with letters and numbers")
                    .build();
        }

        user.setPassword(passwordEncryptor.encrypt(newPassword));
        user.setForceChangePassword(false);
        users.put(username, user);
        saveToFile();
    }

    public void changeNickname(String username, String nickname) {
        UserInfo user = users.get(username);
        if (user == null) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_found")
                    .msg("User does not exist")
                    .build();
        }

        if (nickname == null || nickname.isEmpty()) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.nickname_required")
                    .msg("Nickname cannot be empty")
                    .build();
        }

        user.setNickname(nickname);
        users.put(username, user);
        saveToFile();
    }

    public UserInfo validatePassword(String username, String password) {
        UserInfo user = users.get(username);
        if (user == null) {
            return null;
        }

        String decryptedPassword = passwordEncryptor.decrypt(user.getPassword());
        if (!decryptedPassword.equals(password)) {
            return null;
        }

        return user;
    }

    public void updateLastLoginTime(String username) {
        UserInfo user = users.get(username);
        if (user != null) {
            user.setLastLoginTime(System.currentTimeMillis());
            users.put(username, user);
            saveToFile();
        }
    }

    public void updateUserStatus(String username, AccountStatusEnum status) {
        UserInfo user = users.get(username);
        if (user == null) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_found")
                    .msg("User does not exist")
                    .build();
        }
        user.setStatus(status);
        users.put(username, user);
        saveToFile();
    }

    public void unblockUser(String username) {
        UserInfo user = users.get(username);
        if (user == null) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_found")
                    .msg("User does not exist")
                    .build();
        }
        user.setStatus(AccountStatusEnum.normal);

        long now = System.currentTimeMillis();
        if (UserRoleEnum.guest.equals(user.getRole())) {
            if (user.getRegisterEnd() == null || user.getRegisterEnd() <= now) {
                user.setRegisterEnd(now + applicationConfig.getUserConfig().getGuestRegisterDurationS() * 1000);
            }
        } else if (UserRoleEnum.user.equals(user.getRole())) {
            if (user.getRegisterEnd() == null || user.getRegisterEnd() <= now) {
                user.setRegisterEnd(now + applicationConfig.getUserConfig().getUserRegisterDurationS() * 1000);
            }
        }

        users.put(username, user);
        saveToFile();
    }

    public void checkRegisterEnd() {
        if (!initialized) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (UserInfo user : users.values()) {
            if (AccountStatusEnum.normal.equals(user.getStatus())
                    && user.getRegisterEnd() != null
                    && now > user.getRegisterEnd()) {
                user.setStatus(AccountStatusEnum.block);
                changed = true;
                log.info("用户 {} registerEnd 已过期，自动封禁", user.getUsername());
            }
        }
        if (changed) {
            saveToFile();
        }
    }

    public UserInfo getCurrentUser() {
        return coreUserService.getCurrentUser();
    }

    public UserInfo getUserByUsername(String username) {
        UserInfo user = users.get(username);
        if (user != null) {
            user.setLoginStatus(sessionService.isUserOnline(username)
                    ? LoginStatusEnum.online : LoginStatusEnum.offline);
        }
        return user;
    }

    public List<BlockedNotification> getNotifications() {
        return new ArrayList<>(blockedNotifications.values());
    }

    public void addBlockedNotification(String username, String reason) {
        BlockedNotification notification = blockedNotifications.get(username);
        if (notification != null) {
            notification.setRequestUnlockDate(System.currentTimeMillis());
            notification.setReason(reason);
        } else {
            notification = new BlockedNotification();
            notification.setUsername(username);
            notification.setRequestUnlockDate(System.currentTimeMillis());
            notification.setReason(reason);
            blockedNotifications.put(username, notification);
        }
        saveNotificationsToFile();
    }

    public void removeBlockedNotification(String username) {
        blockedNotifications.remove(username);
        saveNotificationsToFile();
    }

    public void requestUnblock(String username, String password, String reason) {
        UserInfo user = users.get(username);
        if (user == null) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_found")
                    .msg("User does not exist")
                    .build();
        }
        String decryptedPassword = passwordEncryptor.decrypt(user.getPassword());
        if (!decryptedPassword.equals(password)) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.password_wrong")
                    .msg("Incorrect password")
                    .build();
        }
        if (!AccountStatusEnum.block.equals(user.getStatus())) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.not_blocked")
                    .msg("User is not blocked")
                    .build();
        }
        sessionService.removeSessionsByUsername(username);
        addBlockedNotification(username, reason);
    }

    private boolean validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 20) {
            return false;
        }

        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return false;
        }

        return PASSWORD_PATTERN.matcher(password).matches();
    }

    private void validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.username_required")
                    .msg("Username cannot be empty")
                    .build();
        }

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw BusinessLogicException.builder()
                    .code(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode())
                    .msgKey("user.username_invalid_format")
                    .msg("Username: letters/underscores/numbers, 3-32 chars, must start with a letter")
                    .build();
        }
    }

    private synchronized void saveToFile() {
        try {
            List<UserInfo> userList = new ArrayList<>(users.values());
            String json = JSON.toJSONString(userList, SerializerFeature.PrettyFormat);
            Files.write(Paths.get(USER_FILE_PATH), json.getBytes(StandardCharsets.UTF_8));
            log.info("User data saved to {}, {} users", USER_FILE_PATH, userList.size());
        } catch (IOException e) {
            log.error("保存用户数据失败：{}", USER_FILE_PATH, e);
        }
    }

    private void loadFromFile() {
        Path path = Paths.get(USER_FILE_PATH);
        if (!Files.exists(path)) {
            log.info("用户文件不存在：{}", USER_FILE_PATH);
            return;
        }
        String json = FileUtil.readString(path.toFile(), StandardCharsets.UTF_8);
        List<UserInfo> userList = JSON.parseArray(json, UserInfo.class);

        if (userList != null) {
            for (UserInfo user : userList) {
                users.put(user.getUsername(), user);
            }
            log.info("Loaded {} users from {}", users.size(), USER_FILE_PATH);
        }

    }

    private void saveNotificationsToFile() {
        try {
            List<BlockedNotification> list = new ArrayList<>(blockedNotifications.values());
            String json = JSON.toJSONString(list, SerializerFeature.PrettyFormat);
            Files.write(Paths.get(NOTIFICATION_FILE_PATH), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("保存封禁通知数据失败", e);
        }
    }

    private void loadNotificationsFromFile() {
        Path path = Paths.get(NOTIFICATION_FILE_PATH);
        if (!Files.exists(path)) {
            return;
        }
        String json = FileUtil.readString(path.toFile(), StandardCharsets.UTF_8);
        List<BlockedNotification> list = JSON.parseArray(json, BlockedNotification.class);
        if (list != null) {
            for (BlockedNotification n : list) {
                blockedNotifications.put(n.getUsername(), n);
            }
        }
    }

}
