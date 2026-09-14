package com.grw.xiaobai.hybrid.llm.controller;

import cn.hutool.core.lang.Pair;
import com.grw.xiaobai.hybrid.llm.utils.trace.IgnoreLog;
import com.grw.xiaobai.hybrid.llm.annotation.UserPermission;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.system.MemoryInfo;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.service.MemoryService;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@Api(tags = "memory config")
@RequestMapping("/memory")
@Validated
@ResponseBody
@UserPermission(UserRoleEnum.admin)
@IgnoreLog
public class MemoryController {
    @Resource
    private MemoryService memoryService;

    @GetMapping("/history_info")
    @Operation(description = "get memory info status,include memory")
    public ResultModel<List<Pair<Long, MemoryInfo>>> gpuInfoList() {
        return ResultModel.OK(memoryService.getMemoryHistory());
    }
}
