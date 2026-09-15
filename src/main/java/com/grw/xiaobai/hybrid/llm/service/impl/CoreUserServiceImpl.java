package com.grw.xiaobai.hybrid.llm.service.impl;

import com.grw.xiaobai.hybrid.llm.constant.TraceConstants;
import com.grw.xiaobai.hybrid.llm.service.CoreUserService;
import com.grw.xiaobai.hybrid.llm.service.SessionService;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.enums.LoginStatusEnum;
import com.grw.xiaobai.hybrid.llm.utils.ThreadLocalContext;
import java.util.Optional;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class CoreUserServiceImpl implements CoreUserService {
    @Resource
    private SessionService sessionService;

    public UserInfo getCurrentUser() {
        UserInfo user = (UserInfo) Optional.ofNullable(ThreadLocalContext.get(TraceConstants.CURRENT_USER_INFO_KEY)).orElse(ThreadLocalContext.get(TraceConstants.WEB_SOCKET_CURRENT_USER_INFO_KEY));
        if (user != null) {
            user.setLoginStatus(sessionService.isUserOnline(user.getUsername())
                    ? LoginStatusEnum.online : LoginStatusEnum.offline);
        }
        return user;
    }
}
