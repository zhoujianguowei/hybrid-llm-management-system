package com.grw.xiaobai.hybrid.llm.entity.user;

import com.alibaba.fastjson.annotation.JSONField;
import com.grw.xiaobai.hybrid.llm.enums.AccountStatusEnum;
import com.grw.xiaobai.hybrid.llm.enums.LoginStatusEnum;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo {
    private String username;
    private String password;
    @JSONField(serialize = false)
    private String confirmPassword;
    private String nickname;
    private UserRoleEnum role;
    private Long registerTime;
    private Boolean forceChangePassword;
    private Long lastLoginTime;
    @JSONField(serialize = true, deserialize = false)
    private LoginStatusEnum loginStatus;
    private AccountStatusEnum status;
    private Long registerEnd;
    @JSONField(serialize = true, deserialize = false)
    private UserContext userContext;
    private String sessionId;
    private String deviceFingerprint;
}