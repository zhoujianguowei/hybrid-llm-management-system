package com.grw.xiaobai.hybrid.llm.controller;

import com.grw.xiaobai.hybrid.llm.annotation.UserPermission;
import com.grw.xiaobai.hybrid.llm.entity.user.BlockedNotification;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.enums.AccountStatusEnum;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.exception.BusinessLogicException;
import com.grw.xiaobai.hybrid.llm.manager.UserContextManager;
import com.grw.xiaobai.hybrid.llm.service.LoginService;
import com.grw.xiaobai.hybrid.llm.service.UserService;
import com.grw.xiaobai.hybrid.llm.utils.EnumUtil;
import io.swagger.annotations.Api;

import java.util.List;
import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(tags = "user management")
@RequestMapping("/file")
@ResponseBody
public class UserManagementController {
    @Resource
    private UserService userService;
    @Resource
    private UserContextManager userContextManager;
    @Resource
    private LoginService loginService;

    @GetMapping("/user/list")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<List<UserInfo>> getUserList() {
        List<UserInfo> users = userService.getUserList();
        return ResultModel.OK(users);
    }

    @GetMapping("/user/current")
    public ResultModel<UserInfo> getCurrentUser() {
        userContextManager.loadUserContextCurrentUser();
        return ResultModel.OK(userService.getCurrentUser());
    }

    @PostMapping("/user/update")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<String> updateUser(
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "registerEnd", required = false) Long registerEnd) {

        UserInfo user = new UserInfo();
        user.setNickname(nickname);
        user.setRole(role != null ? EnumUtil.getEnumByPropertyValue(UserRoleEnum.class, UserRoleEnum::getCode, role) : null);
        user.setPassword(password);
        if (status != null) {
            user.setStatus(AccountStatusEnum.valueOf(status));
        }
        user.setRegisterEnd(registerEnd);

        if (username != null && !username.isEmpty()) {
            userService.updateUserByUsername(username, user);
        } else {
            userService.updateUser(user);
        }
        return ResultModel.OK("action.success", "Operation successful");
    }

    @PostMapping("/user/update-status")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<String> updateUserStatus(
            @RequestParam("username") String username,
            @RequestParam("status") String status) {
        AccountStatusEnum accountStatus;
        try {
            accountStatus = AccountStatusEnum.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw BusinessLogicException.builder().msg("Invalid status value").msgKey("user.invalid_status").build();
        }
        userService.updateUserStatus(username, accountStatus);
        return ResultModel.OK("action.success", "Operation successful");
    }

    @PostMapping("/user/unblock")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<String> unblockUser(@RequestParam("username") String username) {
        userService.unblockUser(username);
        return ResultModel.OK("user.unblock_success", "Unblock successful");
    }

    @GetMapping("/user/block-notifications")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<List<BlockedNotification>> getBlockedNotifications() {
        return ResultModel.OK(userService.getNotifications());
    }

    @PostMapping("/user/handle-block-notification")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<String> handleBlockedNotification(@RequestParam("username") String username) {
        userService.removeBlockedNotification(username);
        return ResultModel.OK("action.success", "Operation successful");
    }

    @PostMapping("/user/register-guest")
    public ResultModel<String> registerGuest(@RequestBody UserInfo user) {
        userService.registerGuest(user);
        return ResultModel.OK("user.register_success", "Registration successful, please login");
    }

    @PostMapping("/user/request-unblock")
    public ResultModel<String> requestUnblock(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(value = "reason", required = false) String reason) {
        userService.requestUnblock(username, password, reason);
        return ResultModel.OK("user.unblock_request_submitted", "Unblock request submitted, contact admin");
    }

    @PostMapping("/user/create")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<String> createUser(@RequestBody UserInfo user) {
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw BusinessLogicException.builder().msg("Password cannot be empty").msgKey("user.password_required").build();
        }
        if (user.getRole() == null) {
            user.setRole(UserRoleEnum.guest);
        }
        if (user.getNickname() == null || user.getNickname().isEmpty()) {
            user.setNickname(user.getUsername());
        }

        userService.createUser(user);
        return ResultModel.OK("user.create_success", "User created successfully");
    }

    @PostMapping("/user/delete")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<String> deleteUser(@RequestParam("username") String username) {
        userService.deleteUser(username);
        return ResultModel.OK("action.delete_success", "Deleted successfully");
    }

    @GetMapping("/user/password")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<String> getPassword(@RequestParam("username") String username) {
        String password = userService.getPassword(username);
        return ResultModel.OK(password);
    }

    @PostMapping("/user/update-self")
    public ResultModel<String> updateSelfInfo(
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "confirmPassword", required = false) String confirmPassword) {

        if (StringUtils.isNotBlank(password)) {
            if (!password.equals(confirmPassword)) {
                throw BusinessLogicException.builder().msg("Passwords do not match").msgKey("user.password_mismatch").build();
            }
        }
        UserInfo update = new UserInfo();
        update.setNickname(nickname);
        if (StringUtils.isNotBlank(password)) {
            update.setPassword(password);
        }
        userService.updateSelf(update);
        if (StringUtils.isNotBlank(password)) {
            return ResultModel.OK("user.modify_relogin", "Modified successfully, please re-login");
        }
        return ResultModel.OK("user.modify_success", "Modified successfully");
    }

    @PostMapping("/user/change-password")
    public ResultModel<String> changePassword(
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword) {

        UserInfo currentUser = userService.getCurrentUser();
        userService.changePassword(currentUser.getUsername(), newPassword, confirmPassword);
        return ResultModel.OK("user.password_change_success", "Password changed, please re-login");
    }

    @PostMapping("/user/change-nickname")
    public ResultModel<String> changeNickname(@RequestParam("nickname") String nickname) {
        UserInfo currentUser = userService.getCurrentUser();
        userService.changeNickname(currentUser.getUsername(), nickname);
        return ResultModel.OK("user.nickname_change_success", "Nickname changed successfully");
    }

    @PostMapping("/user/force-password")
    public ResultModel<Boolean> forceChangePassword(
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword) {

        if (!newPassword.equals(confirmPassword)) {
            throw BusinessLogicException.builder().msg("Passwords do not match").msgKey("user.password_mismatch").build();
        }

        String sessionId = userService.getCurrentUser().getSessionId();
        return loginService.forceChangePassword(sessionId, newPassword);
    }
}
