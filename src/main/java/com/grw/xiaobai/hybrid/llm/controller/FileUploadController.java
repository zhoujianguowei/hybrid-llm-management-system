package com.grw.xiaobai.hybrid.llm.controller;

import com.grw.xiaobai.hybrid.llm.annotation.FilePermission;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadChunk;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadComplete;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadInit;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadTask;
import com.grw.xiaobai.hybrid.llm.enums.FilePermissionEnum;
import com.grw.xiaobai.hybrid.llm.service.UploadTaskService;
import io.swagger.annotations.Api;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Api(tags = "file upload")
@RequestMapping("/file")
@ResponseBody
public class FileUploadController {

    @Resource
    private UploadTaskService uploadTaskService;

    @PostMapping("/upload/init")
    @FilePermission(value = FilePermissionEnum.UPLOAD, pathParam = "targetPath", rejectKey = "file.upload_permission_denied")
    public ResultModel<UploadInit> initUpload(@RequestParam("fileName") String fileName,
                                               @RequestParam("targetPath") String targetPath,
                                               @RequestParam("totalSize") Long totalSize) throws Exception {
        UploadInit result = uploadTaskService.initUpload(fileName, targetPath, totalSize);
        return ResultModel.OK(result);
    }

    @PostMapping("/upload/chunk")
    public ResultModel<UploadChunk> uploadChunk(@RequestParam("taskId") String taskId,
                                                 @RequestParam("chunkIndex") Long chunkIndex,
                                                 @RequestParam("chunkSize") Long chunkSize,
                                                 @RequestParam("offset") Long offset,
                                                 @RequestParam("totalChunks") Long totalChunks,
                                                 @RequestParam("file") MultipartFile chunk) throws IOException {
        try (InputStream chunkStream = chunk.getInputStream()) {
            UploadChunk result = uploadTaskService.uploadChunk(taskId, chunkIndex, chunkSize, offset, chunkStream);
            return ResultModel.OK(result);
        }
    }

    @PostMapping("/upload/complete")
    public ResultModel<UploadComplete> completeUpload(@RequestParam("taskId") String taskId) {
        UploadComplete result = uploadTaskService.completeUpload(taskId);
        return ResultModel.OK(result);
    }

    @GetMapping("/upload/list")
    public ResultModel<List<UploadTask>> getUploadList() {
        List<UploadTask> result = uploadTaskService.getUploadList();
        return ResultModel.OK(result);
    }

    @PostMapping("/upload/check-exists")
    public ResultModel<Map<String, Boolean>> checkFileExists(@RequestParam("targetPath") String targetPath,
                                                              @RequestParam("fileNames") List<String> fileNames) {
        Map<String, Boolean> result = uploadTaskService.checkFileExists(targetPath, fileNames);
        return ResultModel.OK(result);
    }

    @PostMapping("/upload/cancel")
    public ResultModel<String> cancelUpload(@RequestParam("taskId") String taskId) {
        String result = uploadTaskService.cancelUpload(taskId);
        return ResultModel.OK(result);
    }

    @PostMapping("/upload/delete")
    public ResultModel<String> deleteUpload(@RequestParam("taskIds") List<String> taskIds) {
        String result = uploadTaskService.deleteUpload(taskIds);
        return ResultModel.OK(result);
    }
}
