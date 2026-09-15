package com.grw.xiaobai.hybrid.llm.controller;

import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatModel;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSession;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSessionConfig;
import com.grw.xiaobai.hybrid.llm.entity.chat.ModelFuncConfig;
import com.grw.xiaobai.hybrid.llm.request.NewSessionRequest;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiLLMConfig;
import com.grw.xiaobai.hybrid.llm.request.SessionListRequest;
import com.grw.xiaobai.hybrid.llm.response.SessionListResponse;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatRuntimeConfig;
import com.grw.xiaobai.hybrid.llm.request.CopyUserMessageRequest;
import com.grw.xiaobai.hybrid.llm.entity.user.UserContext;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.utils.image.MediaCacheHandler;
import com.grw.xiaobai.hybrid.llm.manager.ChatSessionConfigManager;
import com.grw.xiaobai.hybrid.llm.manager.UserContextManager;
import com.grw.xiaobai.hybrid.llm.service.ChatService;
import com.grw.xiaobai.hybrid.llm.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Api(tags = "chat management")
@RequestMapping("/chat")
@ResponseBody
public class ChatController {

    @Resource
    private ChatService chatService;

    @Resource
    private ChatSessionConfigManager sessionConfigManager;

    @Resource
    private UserService userService;

    @Resource
    private UserContextManager userContextManager;

    private static final String RESOURCE_DIR = FilePathConstants.RESOURCE_DIR;

    @PostMapping("/sessions/list")
    @ApiOperation("Get session list")
    public ResultModel<SessionListResponse> getSessionList(@RequestBody SessionListRequest request) {
        return ResultModel.OK(chatService.getSessionList(request.getOffset(), request.getLimit()));
    }

    @PostMapping("/sessions/create")
    @ApiOperation("Create new session")
    public ResultModel<ChatSession> createSession(@RequestBody NewSessionRequest request) {
        return ResultModel.OK(chatService.createSession(request));
    }

    @PostMapping("/sessions/load")
    @ApiOperation("Load session")
    public ResultModel<ChatSession> loadSession(@RequestParam("chatSessionId") String chatSessionId) {

        ChatSession chatSession = chatService.loadSession(chatSessionId);
        if (chatSession != null) {
            UserContext userContext = userContextManager.loadUserContextCurrentUser();
            userContext.getChatContext().setLastOpenedChatId(chatSessionId);
        }
        return ResultModel.OK(chatSession);
    }

    @PostMapping("/sessions/delete")
    @ApiOperation("Delete session")
    public ResultModel<Boolean> deleteSession(@RequestParam("chatId") String chatId) {
        return ResultModel.OK(chatService.deleteSession(chatId));
    }

    @PostMapping("/sessions/pin")
    @ApiOperation("Pin/unpin session")
    public ResultModel<Boolean> pinSession(@RequestParam("chatId") String chatId, @RequestParam("type") Integer type) {
        if (type == 1) {
            ChatSessionConfig config = sessionConfigManager.getConfigByRole(userService.getCurrentUser().getRole());
            if (config != null) {
                int pinnedCount = chatService.getPinnedSessions(0, Integer.MAX_VALUE).getTotal();
                if (pinnedCount >= config.getPinnedSessionLimit()) {
                    return ResultModel.fail("chat.pin_limit_reached", "Pinned session limit reached (" + config.getPinnedSessionLimit() + ")");
                }
            }
        }
        chatService.updateSessionType(chatId, type);
        return ResultModel.OK(true);
    }

    @PostMapping("/sessions/pinned")
    @ApiOperation("Get pinned session list")
    public ResultModel<SessionListResponse> getPinnedSessions(@RequestBody SessionListRequest request) {
        return ResultModel.OK(chatService.getPinnedSessions(request.getOffset(), request.getLimit()));
    }

    @PostMapping("/context")
    @ApiOperation("Save user context")
    public ResultModel<Boolean> saveUserContext(@RequestBody UserContext userContext) {
        userContextManager.saveUserContext(userContext);
        return ResultModel.OK(true);
    }

    @PostMapping("/sessions/rename")
    @ApiOperation("Rename session title")
    public ResultModel<Boolean> renameSession(@RequestParam("chatId") String chatId, @RequestParam("title") String title) {
        chatService.updateSessionTitle(chatId, title);
        return ResultModel.OK(true);
    }

    @PostMapping("/sessions/thinking-mode")
    @ApiOperation("Update session thinking mode")
    public ResultModel<Boolean> updateThinkingMode(@RequestParam("chatId") String chatId, @RequestParam("thinkingMode") String thinkingMode) {
        chatService.updateSessionThinkingMode(chatId, thinkingMode);
        return ResultModel.OK(true);
    }

    @PostMapping("/sessions/runtime-config")
    @ApiOperation("Update session runtime config")
    public ResultModel<Boolean> updateSessionRuntimeConfig(@RequestParam("chatId") String chatId,
                                                           @RequestBody ChatRuntimeConfig runtimeConfig) {
        chatService.updateSessionRuntimeConfig(chatId, runtimeConfig);
        return ResultModel.OK(true);
    }

    @PostMapping("/model/list")
    @ApiOperation("Get model list")
    public ResultModel<List<ChatModel>> getModelList() {
        return ResultModel.OK(chatService.getAccessibleModelList());
    }

    @PostMapping("/config/api")
    @ApiOperation("Get API config")
    public ResultModel<List<OpenApiLLMConfig>> getApiConfigs() {
        return ResultModel.OK(chatService.getApiConfigs());
    }

    @PostMapping("/config/model-func")
    @ApiOperation("Get model function config")
    public ResultModel<List<ModelFuncConfig>> getModelFuncConfigs() {
        return ResultModel.OK(chatService.getModelFuncConfigs());
    }

    @PostMapping("/config/model-func/delete")
    @ApiOperation("Delete model function config")
    public ResultModel<Boolean> deleteModelFuncConfig(@RequestParam("configId") String configId) {
        return ResultModel.OK(chatService.deleteModelFuncConfig(configId));
    }

    @PostMapping("/media/delete")
    @ApiOperation("Delete media file")
    public ResultModel<Boolean> deleteMedia(@RequestParam("url") String url) {
        MediaCacheHandler.cleanupMediaFiles(Lists.newArrayList(url));
        return ResultModel.OK(true);
    }

    @GetMapping("/media/text")
    @ApiOperation("Get text file content")
    public ResultModel<String> getTextContent(@RequestParam("url") String url) {
        String content = MediaCacheHandler.convertUrlToText(url);
        if (content.isEmpty()) {
            return ResultModel.fail("chat.file_content_failed", "Failed to get file content");
        }
        return ResultModel.OK(content);
    }

    @PostMapping("/media/upload")
    @ApiOperation("Upload media file")
    public ResultModel<String> uploadMedia(HttpServletRequest request, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResultModel.fail("chat.upload_file_empty", "Uploaded file is empty");
        }
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase() : "";
        ChatSessionConfig config = sessionConfigManager.getConfigByRole(userService.getCurrentUser().getRole());
        if (config != null) {
            boolean isTextFile = isTextFileExtension(ext) && config.getMaxTextAttachmentSize() > 0;
            long maxSizeBytes;
            if (isTextFile) {
                maxSizeBytes = config.getMaxTextAttachmentSize() * 1024L;
                if (file.getSize() > maxSizeBytes) {
                    return ResultModel.fail("chat.file_size_limit", "File size exceeds limit (" + config.getMaxTextAttachmentSize() + "KB)");
                }
            } else {
                maxSizeBytes = config.getMaxAttachmentSize() * 1024L * 1024L;
                if (file.getSize() > maxSizeBytes) {
                    return ResultModel.fail("chat.file_size_limit", "File size exceeds limit (" + config.getMaxAttachmentSize() + "MB)");
                }
            }
        }
        File resourceDir = new File(RESOURCE_DIR);
        if (!resourceDir.exists()) {
            resourceDir.mkdirs();
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        String filename = Instant.now().getEpochSecond() + "_" + System.nanoTime() + "_" + RandomStringUtils.randomAlphabetic(8) + extension;
        File destFile = new File(resourceDir, filename);

        try {
            file.transferTo(destFile);
        } catch (IOException e) {
            return ResultModel.fail("chat.upload_failed", "File upload failed: " + e.getMessage());
        }

        String baseUrl = request.getRequestURL().toString().replace("/chat/media/upload", "/chat/media/");
        return ResultModel.OK(baseUrl + filename);
    }

    @GetMapping("/media/{filename:.+}")
    @ApiOperation("Get media file")
    public ResponseEntity<byte[]> getMedia(@PathVariable String filename,
                                            @RequestParam(value = "width", required = false) Integer width,
                                            @RequestParam(value = "height", required = false) Integer height,
                                            @RequestParam(value = "quality", required = false) Float quality) {
        try {
            byte[] compressedBytes = MediaCacheHandler.compressImageToBytes(filename, width, height, quality);
            if (compressedBytes.length == 0) {
                return ResponseEntity.status(500).build();
            }
            String contentType = getMediaType(filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(compressedBytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    private String getMediaType(String filename) {
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
        switch (ext.toLowerCase()) {
            case ".jpg":
            case ".jpeg":
                return "image/jpeg";
            case ".png":
                return "image/png";
            case ".gif":
                return "image/gif";
            case ".webp":
                return "image/webp";
            case ".svg":
                return "image/svg+xml";
            case ".bmp":
                return "image/bmp";
            case ".mp4":
                return "video/mp4";
            case ".webm":
                return "video/webm";
            case ".ogg":
                return "video/ogg";
            case ".mp3":
                return "audio/mpeg";
            case ".wav":
                return "audio/wav";
            case ".aac":
                return "audio/aac";
            case ".flac":
                return "audio/flac";
            default:
                return "application/octet-stream";
        }
    }

    private boolean isTextFileExtension(String ext) {
        String lowerExt = ext.toLowerCase();
        return lowerExt.equals(".txt") || lowerExt.equals(".md") || lowerExt.equals(".csv") || lowerExt.equals(".log")
                || lowerExt.equals(".tex") || lowerExt.equals(".rst") || lowerExt.equals(".html") || lowerExt.equals(".htm")
                || lowerExt.equals(".css") || lowerExt.equals(".scss") || lowerExt.equals(".less") || lowerExt.equals(".xml")
                || lowerExt.equals(".json") || lowerExt.equals(".yaml") || lowerExt.equals(".yml") || lowerExt.equals(".vue")
                || lowerExt.equals(".svg") || lowerExt.equals(".java") || lowerExt.equals(".kt") || lowerExt.equals(".kts")
                || lowerExt.equals(".scala") || lowerExt.equals(".groovy") || lowerExt.equals(".gradle") || lowerExt.equals(".properties")
                || lowerExt.equals(".js") || lowerExt.equals(".ts") || lowerExt.equals(".jsx") || lowerExt.equals(".tsx")
                || lowerExt.equals(".py") || lowerExt.equals(".c") || lowerExt.equals(".cpp") || lowerExt.equals(".cc")
                || lowerExt.equals(".cxx") || lowerExt.equals(".h") || lowerExt.equals(".hpp") || lowerExt.equals(".go")
                || lowerExt.equals(".rs") || lowerExt.equals(".rb") || lowerExt.equals(".php") || lowerExt.equals(".swift")
                || lowerExt.equals(".sh") || lowerExt.equals(".bash") || lowerExt.equals(".bat") || lowerExt.equals(".sql") || lowerExt.equals(".r")
                || lowerExt.equals(".pl") || lowerExt.equals(".lua") || lowerExt.equals(".hs") || lowerExt.equals(".ex")
                || lowerExt.equals(".exs") || lowerExt.equals(".clj") || lowerExt.equals(".fs") || lowerExt.equals(".fsx")
                || lowerExt.equals(".toml") || lowerExt.equals(".ini") || lowerExt.equals(".cfg") || lowerExt.equals(".conf")
                || lowerExt.equals(".dart") || lowerExt.equals(".pot") || lowerExt.equals(".po") || lowerExt.equals(".v")
                || lowerExt.equals(".sv") || lowerExt.equals(".asm") || lowerExt.equals(".s");
    }

    @PostMapping("/user-message/copy-text")
    @ApiOperation("Copy user message text")
    public ResultModel<String> copyUserMessageText(@RequestBody CopyUserMessageRequest request) {
        String result = chatService.copyUserMessageText(request);
        return ResultModel.OK(result);
    }
}
