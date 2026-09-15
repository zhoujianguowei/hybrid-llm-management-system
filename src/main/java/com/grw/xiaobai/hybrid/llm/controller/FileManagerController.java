package com.grw.xiaobai.hybrid.llm.controller;

import cn.hutool.core.io.FileUtil;
import com.grw.xiaobai.hybrid.llm.annotation.FilePermission;
import com.grw.xiaobai.hybrid.llm.constant.ErrorCodeConstants;
import com.grw.xiaobai.hybrid.llm.entity.file.FileInfo;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.entity.file.DownloadTask;
import com.grw.xiaobai.hybrid.llm.enums.FilePermissionEnum;
import com.grw.xiaobai.hybrid.llm.exception.BusinessLogicException;
import com.grw.xiaobai.hybrid.llm.service.DownloadTaskService;
import com.grw.xiaobai.hybrid.llm.service.FileManagerService;
import com.grw.xiaobai.hybrid.llm.service.FilePermissionService;
import com.grw.xiaobai.hybrid.llm.service.UserService;
import io.swagger.annotations.Api;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.NotBlank;

@RestController
@Api(tags = "file manager")
@Validated
@RequestMapping("/file")
@ResponseBody
public class FileManagerController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileManagerController.class);

    @Resource
    private FileManagerService fileManagerService;

    @Resource
    private DownloadTaskService downloadTaskService;

    @Resource
    private UserService userService;

    @Resource
    private FilePermissionService filePermissionService;

    @GetMapping("/roots")
    public ResultModel<List<FileInfo>> getRootDirectories(@RequestParam(value = "rememberPath", defaultValue = "true") boolean rememberPath) {
        if (rememberPath) {
            fileManagerService.clearLastVisitedFileDir();
        }
        List<String> roots = fileManagerService.getRootDirectories();
        List<FileInfo> result = new ArrayList<>();
        UserInfo user = userService.getCurrentUser();
        for (String root : roots) {
            if (filePermissionService.checkPermission(root, FilePermissionEnum.READ.getMask())) {
                FileInfo info = FileInfo.fromFile(new File(root));
                info.setFilePermission(filePermissionService.computeFilePermission(info, user));
                result.add(info);
            }
        }
        return ResultModel.OK(result);
    }

    @GetMapping("/list")
    @FilePermission(value = FilePermissionEnum.EXECUTE, rejectKey = "file.execute_permission_denied")
    public ResultModel<List<FileInfo>> listFiles(@RequestParam("path") String path, @RequestParam(value = "rememberPath", defaultValue = "true") boolean rememberPath, HttpServletRequest request) {
        List<FileInfo> files = fileManagerService.listFilesAndSavePath(path, rememberPath);
        if (files == null) {
            return ResultModel.fail(ErrorCodeConstants.NOT_FOUND, "file.dir_not_found", "Directory does not exist");
        }
        UserInfo user = userService.getCurrentUser();
        List<FileInfo> result = new ArrayList<>();
        for (FileInfo file : files) {
            int readMask = FilePermissionEnum.READ.getMask();
            if (!filePermissionService.checkPermission(file.getPath(), readMask)) {
                continue;
            }
            file.setFilePermission(filePermissionService.computeFilePermission(file, user));
            result.add(file);
        }
        return ResultModel.OK(result);
    }

    @GetMapping("/preview")
    @FilePermission(value = FilePermissionEnum.READ, rejectKey = "file.read_permission_denied")
    public void previewFile(@RequestParam("path") String path,
                            HttpServletRequest request, HttpServletResponse response) throws Exception {
        String rangeHeader = request.getHeader("Range");
        FileManagerService.PreviewInfo previewInfo = fileManagerService.getPreviewInfo(path, rangeHeader);

        if (!previewInfo.isExists()) {
            LOGGER.error("Preview file not found: {}", path);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        LOGGER.info("Preview request - path: {}, mimeType: {}, fileSize: {}, rangeHeader: {}",
                path, previewInfo.getMimeType(), previewInfo.getFileSize(), rangeHeader);

        response.setHeader("Accept-Ranges", "bytes");

        FileManagerService.RangeInfo rangeInfo = previewInfo.getRangeInfo();

        if (rangeHeader != null && previewInfo.isMedia() && !rangeInfo.isValid()) {
            if (rangeInfo.getFileSize() == 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } else {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + previewInfo.getFileSize());
            }
            return;
        }

        fileManagerService.writeFileToResponseWithRange(path, previewInfo, response);
    }

    @GetMapping("/download/file")
    @FilePermission(value = FilePermissionEnum.DOWNLOAD, rejectKey = "file.download_permission_denied")
    public void downloadFile(@RequestParam("path") String path,
                             HttpServletRequest request, HttpServletResponse response) throws Exception {
        FileManagerService.DownloadInfo downloadInfo = fileManagerService.getDownloadInfo(path);

        if (!downloadInfo.isExists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String rangeHeader = request.getHeader("Range");
        fileManagerService.setupAndWriteDownloadResponseWithRange(path, downloadInfo, null, rangeHeader, response);
    }

    @GetMapping("/download/directory")
    public void downloadDirectoryFile(@RequestParam("taskId") String taskId,
                                      HttpServletRequest request, HttpServletResponse response) throws Exception {
        String sourcePath = downloadTaskService.getSourcePath(taskId);
        if (sourcePath == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (!filePermissionService.checkPermission(sourcePath, FilePermissionEnum.DOWNLOAD.getMask())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String outputPath = downloadTaskService.getOutputPath(taskId);
        if (StringUtils.isBlank(outputPath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        FileManagerService.DownloadInfo downloadInfo = fileManagerService.getTaskDownloadInfo(outputPath);
        if (downloadInfo == null || !downloadInfo.isExists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String rangeHeader = request.getHeader("Range");
        String renameFileName = downloadInfo.getFileName().startsWith(taskId + "_") ?
                downloadInfo.getFileName().substring(taskId.length() + 1) : downloadInfo.getFileName();
        fileManagerService.setupAndWriteDownloadResponseWithRange(outputPath, downloadInfo, renameFileName, rangeHeader, response);
    }

    @PostMapping("/create/download_task")
    @FilePermission(value = FilePermissionEnum.DOWNLOAD, rejectKey = "file.dir_download_permission_denied")
    public ResultModel<String> createDownloadTask(@RequestParam("path") String path) {
        if (!fileManagerService.fileExists(path)) {
            throw BusinessLogicException.builder().msgKey("file.not_found").msg("File not found: " + path).build();
        }

        File file = new File(path);
        if (!file.isDirectory()) {
            throw BusinessLogicException.builder().msgKey("file.use_direct_download").msg("Files should use direct download").build();
        }

        String username = userService.getCurrentUser().getUsername();
        String taskId = downloadTaskService.createDownloadTask(username, path);
        return ResultModel.OK(taskId);
    }

    @GetMapping("/task/list")
    public ResultModel<List<DownloadTask>> getTaskList() {
        String username = userService.getCurrentUser().getUsername();
        List<DownloadTask> tasks = downloadTaskService.getTasksByUsername(username);
        tasks.sort((a, b) -> Long.compare(b.getCreatedTime(), a.getCreatedTime()));
        return ResultModel.OK(tasks);
    }

    @PostMapping("/delete")
    @FilePermission(value = FilePermissionEnum.DELETE, rejectKey = "file.delete_permission_denied")
    public ResultModel<String> deleteFile(@RequestParam("path") String path) {
        boolean success = fileManagerService.deleteFile(path);
        if (success) {
            return ResultModel.OK("action.delete_success", "Deleted successfully");
        } else {
            return ResultModel.fail(ErrorCodeConstants.NOT_FOUND, "file.not_found", "File or directory does not exist");
        }
    }

    @PostMapping("/create/folder")
    @FilePermission(value = FilePermissionEnum.UPLOAD, rejectKey = "file.create_folder_permission_denied")
    public ResultModel<String> createFolder(@RequestParam("path") @NotBlank String path, @RequestParam("name") @NotBlank String name) {
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return ResultModel.fail(ErrorCodeConstants.BAD_REQUEST, "file.folder_name_invalid", "Invalid folder name");
        }
        String fullPath = FileUtil.normalize(path + "/" + name);
        File dirFile = new File(fullPath);
        if (dirFile.exists() && dirFile.isDirectory()) {
            return ResultModel.fail(ErrorCodeConstants.BAD_REQUEST, "file.folder_already_exists", "Folder already exists");
        }
        boolean success = fileManagerService.createDirectory(fullPath);
        if (success) {
            return ResultModel.OK("action.create_folder_success", "Folder created successfully");
        } else {
            throw BusinessLogicException.builder().msgKey("file.create_folder_failed")
                    .msg("Failed to create folder").build();
        }
    }
}
