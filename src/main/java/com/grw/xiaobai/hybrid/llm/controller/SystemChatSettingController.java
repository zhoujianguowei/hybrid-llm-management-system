package com.grw.xiaobai.hybrid.llm.controller;

import com.grw.xiaobai.hybrid.llm.annotation.UserPermission;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSessionConfig;
import com.grw.xiaobai.hybrid.llm.entity.chat.ModelFuncConfig;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiLLMConfig;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.manager.ChatSessionConfigManager;
import com.grw.xiaobai.hybrid.llm.service.ChatService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(tags = "chat system settings")
@RequestMapping("/chat/system-setting")
@ResponseBody
@UserPermission(UserRoleEnum.admin)
public class SystemChatSettingController {

    @Resource
    private ChatService chatService;

    @Resource
    private ChatSessionConfigManager sessionConfigManager;

    @PostMapping("/api-configs")
    @ApiOperation("Get API config list")
    public ResultModel<List<OpenApiLLMConfig>> getApiConfigs() {
        return ResultModel.OK(chatService.getApiConfigs());
    }

    @PostMapping("/api-configs/save")
    @ApiOperation("Save API config")
    public ResultModel<OpenApiLLMConfig> saveApiConfig(@RequestBody OpenApiLLMConfig config) {
        return chatService.saveApiConfig(config);
    }

    @PostMapping("/api-configs/delete")
    @ApiOperation("Delete API config")
    public ResultModel<Boolean> deleteApiConfig(@RequestParam("configId") String configId) {
        return ResultModel.OK(chatService.deleteApiConfig(configId));
    }

    @PostMapping("/model-func-configs")
    @ApiOperation("Get model function config list")
    public ResultModel<List<ModelFuncConfig>> getModelFuncConfigs() {
        return ResultModel.OK(chatService.getModelFuncConfigs());
    }

    @PostMapping("/model-func-configs/save")
    @ApiOperation("Save model function config")
    public ResultModel<ModelFuncConfig> saveModelFuncConfig(@RequestBody ModelFuncConfig config) {
        return chatService.saveModelFuncConfig(config);
    }

    @PostMapping("/model-func-configs/delete")
    @ApiOperation("Delete model function config")
    public ResultModel<Boolean> deleteModelFuncConfig(@RequestParam("configId") String configId) {
        return ResultModel.OK(chatService.deleteModelFuncConfig(configId));
    }

    @PostMapping("/model-func-configs/move")
    @ApiOperation("Adjust model function config order")
    public ResultModel<Boolean> moveModelFuncOrder(@RequestParam("configId") String configId, @RequestParam("up") boolean up) {
        return ResultModel.OK(chatService.moveModelFuncOrder(configId, up));
    }

    @PostMapping("/session-configs")
    @ApiOperation("Get session config list")
    public ResultModel<List<ChatSessionConfig>> getSessionConfigs() {
        return ResultModel.OK(sessionConfigManager.getConfigs());
    }

    @PostMapping("/session-configs/save")
    @ApiOperation("Save session config")
    public ResultModel<ChatSessionConfig> saveSessionConfig(@RequestBody ChatSessionConfig config) {
        return sessionConfigManager.saveConfig(config);
    }
}
