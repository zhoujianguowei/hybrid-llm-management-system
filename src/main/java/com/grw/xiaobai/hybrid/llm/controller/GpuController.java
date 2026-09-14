package com.grw.xiaobai.hybrid.llm.controller;

import cn.hutool.core.lang.Pair;
import com.grw.xiaobai.hybrid.llm.utils.trace.IgnoreLog;
import com.grw.xiaobai.hybrid.llm.annotation.UserPermission;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.system.GpuInfo;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.service.GpuService;
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
@Api(tags = "gpu config")
@RequestMapping("/gpu")
@Validated
@ResponseBody
@UserPermission(UserRoleEnum.admin)
@IgnoreLog
public class GpuController {

    @Resource
    private GpuService gpuService;

    @GetMapping("/history_info")
    @Operation(description = "get gpu info status,include memory")
    public ResultModel<List<Pair<Long, List<GpuInfo>>>> gpuInfoList() {
        return ResultModel.OK(gpuService.getGpuHistoryInfo());
    }

    @GetMapping("/list")
    @Operation(description = "get available gpu list")
    public ResultModel<List<GpuInfo>> gpuList() {
        return ResultModel.OK(gpuService.getGpuList());
    }
}
