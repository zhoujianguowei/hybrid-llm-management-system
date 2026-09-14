package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.user.BlockedNotification;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.enums.AccountStatusEnum;
import org.aspectj.lang.ProceedingJoinPoint;

import java.util.List;

public interface UserService {
    void init();

    List<UserInfo> getUserList();

    void createUser(UserInfo user);

    void registerGuest(UserInfo user);

    void updateUser(UserInfo user);

    void updateUserByUsername(String username, UserInfo user);

    void updateSelf(UserInfo updateUser);

    void deleteUser(String username);

    String getPassword(String username);

    void changePassword(String username, String newPassword, String confirmPassword);

    void changeNickname(String username, String nickname);

    UserInfo validatePassword(String username, String password);

    void updateLastLoginTime(String username);

    void updateUserStatus(String username, AccountStatusEnum status);

    void unblockUser(String username);

    void checkRegisterEnd();

    UserInfo getCurrentUser();

    UserInfo getUserByUsername(String username);

    List<BlockedNotification> getNotifications();

    void addBlockedNotification(String username, String reason);

    void removeBlockedNotification(String username);

    void requestUnblock(String username, String password, String reason);

    void addDeleteListener(UserDeleteListener listener);

    Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable;

    interface UserDeleteListener {
        void onUserDelete(String username);
    }
}
