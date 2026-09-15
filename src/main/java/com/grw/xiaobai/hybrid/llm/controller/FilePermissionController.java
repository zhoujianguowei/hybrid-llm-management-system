package com.grw.xiaobai.hybrid.llm.controller;

import com.grw.xiaobai.hybrid.llm.utils.ValidationGroup;
import com.grw.xiaobai.hybrid.llm.annotation.UserPermission;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.file.FilePermissionRule;
import com.grw.xiaobai.hybrid.llm.entity.file.FilePermissionTree;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.service.FilePermissionService;
import io.swagger.annotations.Api;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.NotBlank;

@RestController
@Api(tags = "file permission")
@RequestMapping("/file/permission")
@ResponseBody
@Validated
public class FilePermissionController {

    @Resource
    private FilePermissionService permissionService;

    @GetMapping("/list")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<FilePermissionTree> listAll() {
        return ResultModel.OK(permissionService.listAll());
    }

    @PostMapping("/save/path")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<String> savePathRule(@Validated(ValidationGroup.InsertOrUpdate.class) @RequestBody FilePermissionRule rule) {
        permissionService.savePathRule(rule);
        return ResultModel.OK("action.save_success", "Saved successfully");
    }

    @PostMapping("/path/delete")
    @UserPermission(UserRoleEnum.admin)
    public ResultModel<String> deletePathRule(@RequestParam("path") @NotBlank String path) {
        permissionService.deletePathRule(path);
        return ResultModel.OK("action.delete_success", "Deleted successfully");
    }

    @GetMapping("/check")
    public ResultModel<Integer> checkPathPermission(@RequestParam("path") @NotBlank String path) {

        return ResultModel.OK(permissionService.computePathPermission(path));
    }
}
