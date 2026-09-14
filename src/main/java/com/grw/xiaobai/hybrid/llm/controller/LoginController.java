package com.grw.xiaobai.hybrid.llm.controller;

import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.service.ApplicationConfigService;
import com.grw.xiaobai.hybrid.llm.service.LoginService;
import io.swagger.annotations.Api;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(tags = "file login")
@RequestMapping("/file")
@ResponseBody
public class LoginController {

    @Resource
    private LoginService loginService;

    @Resource
    private ApplicationConfigService applicationConfigService;

    @PostMapping("/login")
    public ResultModel<String> login(@RequestParam("username") String username, @RequestParam("password") String password) {
        return loginService.login(username, password);
    }

    @GetMapping("/logout")
    public ResultModel<Boolean> logout(@RequestParam("sessionId") String sessionId) {
        loginService.logout(sessionId);
        return ResultModel.OK(true);
    }

    @GetMapping("/validate")
    public ResultModel<String> validateSession(@RequestParam("sessionId") String sessionId) {
        return loginService.validateSession(sessionId);
    }

    @GetMapping("/guest-register-enabled")
    public ResultModel<Boolean> isGuestRegisterEnabled() {
        return ResultModel.OK(applicationConfigService.isGuestRegisterEnabled());
    }

    @GetMapping("/guest-register-info")
    public ResultModel<Map<String, Object>> getGuestRegisterInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("enabled", applicationConfigService.isGuestRegisterEnabled());
        info.put("durationDays", applicationConfigService.getGuestRegisterDurationDays());
        return ResultModel.OK(info);
    }

    @GetMapping("/version")
    public ResultModel<String> getVersion() {
        return ResultModel.OK(applicationConfigService.getVersion());
    }
}
