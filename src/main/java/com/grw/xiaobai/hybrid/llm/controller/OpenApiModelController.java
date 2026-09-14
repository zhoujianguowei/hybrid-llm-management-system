package com.grw.xiaobai.hybrid.llm.controller;

import com.grw.xiaobai.hybrid.llm.entity.chat.ChatModel;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiLLMConfig;
import com.grw.xiaobai.hybrid.llm.response.OpenApiModelTestResp;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.service.OpenApiModelService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(tags = "OpenAPI model service")
@RequestMapping("/chat/openapi")
@ResponseBody
public class OpenApiModelController {

    @Resource
    private OpenApiModelService openApiModelService;

    @PostMapping("/test")
    @ApiOperation("Test OpenAPI connection")
    public ResultModel<OpenApiModelTestResp> testConnection(@RequestBody OpenApiLLMConfig config) {
        OpenApiModelTestResp resp = new OpenApiModelTestResp();

        if (config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
            resp.setErrorMsg("URL cannot be empty");
            return ResultModel.OK(resp);
        }

        StringBuilder errorMsgBuilder = new StringBuilder();
        List<ChatModel> models = openApiModelService.scanRunningModelList(config, errorMsgBuilder);

        if (errorMsgBuilder.length() > 0) {
            resp.setErrorMsg(errorMsgBuilder.toString());
        } else {
            resp.setModelNames(models.stream().map(ChatModel::getModelName).collect(Collectors.toList()));
        }

        return ResultModel.OK(resp);
    }
}
