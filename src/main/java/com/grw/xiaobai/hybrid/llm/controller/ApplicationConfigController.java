package com.grw.xiaobai.hybrid.llm.controller;

import com.grw.xiaobai.hybrid.llm.annotation.UserPermission;
import com.grw.xiaobai.hybrid.llm.config.ApplicationConfig;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.service.ApplicationConfigService;
import io.swagger.annotations.Api;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import javax.validation.constraints.NotBlank;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(tags = "system config")
@RequestMapping("/config")
@Validated
@ResponseBody
@UserPermission(UserRoleEnum.admin)
public class ApplicationConfigController {

    @Resource
    private ApplicationConfigService applicationConfigService;

    @GetMapping("/fetch/config")
    public ResultModel<ApplicationConfig> getApplicationConfig() {
        return ResultModel.OK(applicationConfigService.getApplicationConfig());
    }

    @PostMapping("/llama-paths/save")
    public ResultModel<Void> saveLlamaPaths(@RequestBody Map<String, String> body) {
        applicationConfigService.saveLlamaPaths(body);
        return ResultModel.OK();
    }

    @GetMapping("/validate-paths")
    public ResultModel<Map<String, Boolean>> validatePaths() {
        return ResultModel.OK(applicationConfigService.validatePaths());
    }

    @GetMapping("/value")
    public ResultModel<String> getConfigValue(@RequestParam("key") @NotBlank String key) {
        return ResultModel.OK(applicationConfigService.getConfigValue(key));
    }

    @PostMapping("/set-value")
    public ResultModel<Void> saveConfigValue(@RequestParam("key") @NotBlank String key, @RequestParam("value") String value) {
        applicationConfigService.saveConfigValue(key, value);
        return ResultModel.OK();
    }

    @PostMapping("/models/save")
    public ResultModel<Void> saveModels(@RequestBody List<ApplicationConfig.CustomModel> models) {
        applicationConfigService.saveModels(models);
        return ResultModel.OK();
    }

 }
