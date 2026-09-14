package com.grw.xiaobai.hybrid.llm.controller;

import com.grw.xiaobai.hybrid.llm.annotation.UserPermission;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.system.SystemInfo;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.service.SystemService;
import io.swagger.annotations.Api;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@Api(tags = "system info")
@RequestMapping("/system")
@Validated
@ResponseBody
@UserPermission(UserRoleEnum.admin)
public class SystemController {

    @Resource
    private SystemService systemService;

    @GetMapping("/platform")
    public ResultModel<String> getPlatform() {
        return ResultModel.OK(systemService.getSystem());
    }

    @GetMapping("/info")
    public ResultModel<SystemInfo> systemInfo() {
        return ResultModel.OK(systemService.getSystemInfo());
    }
}
