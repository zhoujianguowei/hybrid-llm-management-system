package com.grw.xiaobai.hybrid.llm.task;

import com.grw.xiaobai.hybrid.llm.constant.ThreadPoolConstants;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatModel;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiLLMConfig;
import com.grw.xiaobai.hybrid.llm.manager.ModelFuncConfigManager;
import com.grw.xiaobai.hybrid.llm.manager.OpenApiLLMConfigManager;
import com.grw.xiaobai.hybrid.llm.service.OpenApiModelService;
import com.grw.xiaobai.hybrid.llm.utils.CompletableFutureUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OpenApiModelScanTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiModelScanTask.class);
    @Resource
    private OpenApiLLMConfigManager apiLLMConfigManager;
    @Resource
    private ModelFuncConfigManager modelFuncConfigManager;
    @Resource(name = ThreadPoolConstants.COMMON_ASYNC_TASK_EXECUTOR_NAME)
    private java.util.concurrent.ExecutorService commonAsyncExecutor;
    @Resource
    private OpenApiModelService openApiModelService;

    private List<ChatModel> chatModelList = new ArrayList<>();

    @PostConstruct
    public void init() {
        modelFuncConfigManager.addChangeListener(this::scanModel);
        scanModel();
    }

    @Scheduled(cron = "*/10 * * * * *")
    public void scanModel() {
        try {
            doScan();
        } catch (Exception e) {
            // 捕获异常避免定时任务被永久取消
            LOGGER.error("Failed to scan models", e);
        }
    }

    private void doScan() {
        List<OpenApiLLMConfig> openApiLLMConfigList = apiLLMConfigManager.getConfigs();
        List<ChatModel> updatedChatModelList = CompletableFutureUtil.asyncApplyAllOf(openApiLLMConfigList,
                        config -> openApiModelService.scanRunningModelList(config), commonAsyncExecutor).join()
                .stream().flatMap(Collection::stream).collect(Collectors.toList());
        synchronized (this) {
            chatModelList = updatedChatModelList;
        }
    }

    public List<ChatModel> currentActiveModelList() {
        synchronized (this) {
            return new ArrayList<>(chatModelList);
        }
    }


}
