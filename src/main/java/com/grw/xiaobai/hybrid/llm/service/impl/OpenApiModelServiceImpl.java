package com.grw.xiaobai.hybrid.llm.service.impl;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.constant.ThreadPoolConstants;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatModel;
import com.grw.xiaobai.hybrid.llm.entity.chat.ModelFuncConfig;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiLLMConfig;
import com.grw.xiaobai.hybrid.llm.enums.ModelTypeEnum;
import com.grw.xiaobai.hybrid.llm.manager.ModelFuncConfigManager;
import com.grw.xiaobai.hybrid.llm.service.OpenApiModelService;
import com.grw.xiaobai.hybrid.llm.utils.JsonValidUtil;
import com.grw.xiaobai.hybrid.llm.utils.CompletableFutureUtil;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenApiModelServiceImpl implements OpenApiModelService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiModelServiceImpl.class);

    private static final int DEFAULT_CONTENT_LENGTH = 8192;

    private static final String OWNED_BY_VLLM = "vllm";
    private static final String OWNED_BY_SGLANG = "sglang";
    private static final String OWNED_BY_LLAMACPP = "llamacpp";
    private static final String OWNED_BY_KTRANSFORMERS = "ktransformers";
    private static final String OWNED_BY_HUGGINGFACE = "huggingface";
    private static final String OWNED_BY_TGI = "text-generation-inference";
    private static final String OWNED_BY_LIBRARY = "library";
    private static final String OWNED_BY_EXV3 = "tabbyapi";
    private static final String EXV3_MODEL_SUFFIX = "-exl3";

    @Resource
    private ModelFuncConfigManager modelFuncConfigManager;
    @Resource(name = ThreadPoolConstants.COMMON_ASYNC_TASK_EXECUTOR_NAME)
    private ExecutorService commonAsyncExecutor;

    public List<ChatModel> scanRunningModelList(OpenApiLLMConfig openApiLLMConfig, StringBuilder errorMsgBuilder) {
        Map<String, String> headerMap = buildHeaders(openApiLLMConfig);
        AtomicReference<String> responseBodyRef = new AtomicReference<>();

        JSONObject modelsResp = httpGet(openApiLLMConfig.getBaseUrl() + "/v1/models", headerMap,
                responseBodyRef, errorMsgBuilder);
        if (modelsResp == null) {
            return Lists.newArrayList();
        }

        JSONArray dataArray = modelsResp.getJSONArray("data");
        if (dataArray == null || dataArray.isEmpty()) {
            LOGGER.warn("open api url={} /v1/models returned empty data", openApiLLMConfig.getBaseUrl());
            return Lists.newArrayList();
        }

        String firstOwnedBy = dataArray.getJSONObject(0).getString("owned_by");
        ModelTypeEnum serverEngine = detectEngine(firstOwnedBy);
        if (serverEngine == ModelTypeEnum.OPENAI && StringUtils.isNotBlank(firstOwnedBy) && isOllamaServer(openApiLLMConfig)) {
            serverEngine = ModelTypeEnum.OLLAMA;
        }
        LOGGER.debug("open api url={} detected engine={}, owned_by={}", openApiLLMConfig.getBaseUrl(), serverEngine, firstOwnedBy);

        Map<String, Integer> ollamaContextLengthMap = null;
        if (serverEngine == ModelTypeEnum.OLLAMA) {
            ollamaContextLengthMap = fetchOllamaContextLengths(openApiLLMConfig, dataArray);
        }
        //if serve engine is exllamav3 ,need remove duplicate
        if (serverEngine == ModelTypeEnum.EXLLAMAV3) {
            Collection<JSONObject> jsonObjectList = IntStream.range(0, dataArray.size()).boxed().map(dataArray::getJSONObject)
                    .collect(Collectors.toMap(var -> var.getLong("created") + var.getJSONObject("meta").toJSONString(),
                            Function.identity(), (v1, v2) -> {
                                String id1 = v1.getString("id");
                                String id2 = v2.getString("id");
                                JSONObject jsonObject = id1.length() >= id2.length() ? v1 : v2;
                                if (id1.endsWith(EXV3_MODEL_SUFFIX)) {
                                    if (id2.endsWith(EXV3_MODEL_SUFFIX)) {
                                        return jsonObject;
                                    }
                                    return v1;
                                }
                                return jsonObject;
                            })).values();
            dataArray.clear();
            dataArray.addAll(jsonObjectList);
        }

        List<ChatModel> result = new ArrayList<>();
        for (int i = 0; i < dataArray.size(); i++) {
            JSONObject modelObj = dataArray.getJSONObject(i);
            ModelTypeEnum engine = serverEngine == ModelTypeEnum.OLLAMA ? ModelTypeEnum.OLLAMA
                    : detectModelEngine(modelObj.getString("owned_by"), serverEngine);
            ChatModel chatModel;
            if (engine == ModelTypeEnum.OLLAMA) {
                int contentLength = ollamaContextLengthMap == null ? DEFAULT_CONTENT_LENGTH
                        : ollamaContextLengthMap.getOrDefault(modelObj.getString("id"), DEFAULT_CONTENT_LENGTH);
                chatModel = parseOllama(modelObj, contentLength);
            } else {
                chatModel = parseByEngine(modelObj, engine);
            }
            chatModel.setOpenApiLLMConfig(openApiLLMConfig);
            result.add(enrichWithFuncConfig(chatModel));
        }
        return result;
    }

    public List<ChatModel> scanRunningModelList(OpenApiLLMConfig openApiLLMConfig) {
        return scanRunningModelList(openApiLLMConfig, new StringBuilder());
    }

    private ModelTypeEnum detectModelEngine(String ownedBy, ModelTypeEnum serverEngine) {
        if (StringUtils.isBlank(ownedBy)) {
            return serverEngine;
        }
        ModelTypeEnum engine = detectEngine(ownedBy);
        return engine == ModelTypeEnum.OPENAI ? serverEngine : engine;
    }

    private ModelTypeEnum detectEngine(String ownedBy) {
        if (StringUtils.isBlank(ownedBy)) {
            return ModelTypeEnum.OPENAI;
        }
        switch (ownedBy.toLowerCase()) {
            case OWNED_BY_VLLM:
                return ModelTypeEnum.VLLM;
            case OWNED_BY_SGLANG:
                return ModelTypeEnum.SGLANG;
            case OWNED_BY_LLAMACPP:
                return ModelTypeEnum.LLAMACPP;
            case OWNED_BY_KTRANSFORMERS:
                return ModelTypeEnum.KTRANSFORMERS;
            case OWNED_BY_HUGGINGFACE:
            case OWNED_BY_TGI:
                return ModelTypeEnum.TGI;
            case OWNED_BY_LIBRARY:
                return ModelTypeEnum.OLLAMA;
            case OWNED_BY_EXV3:
                return ModelTypeEnum.EXLLAMAV3;
            default:
                return ModelTypeEnum.OPENAI;
        }
    }

    private ChatModel parseByEngine(JSONObject modelObj, ModelTypeEnum engine) {
        ChatModel chatModel;
        switch (engine) {
            case VLLM:
            case SGLANG:
                chatModel = parseVllmSglang(modelObj);
                break;
            case LLAMACPP:
                chatModel = parseLlamaCpp(modelObj);
                break;
            case TGI:
                chatModel = parseTgi(modelObj);
                break;
            case KTRANSFORMERS:
                chatModel = parseKtransformers(modelObj);
                break;
            case EXLLAMAV3:
                chatModel = parseExllamav3(modelObj);
                break;
            case OPENAI:
            default:
                chatModel = parseOpenAi(modelObj);
        }
        chatModel.setModelTypeEnum(engine);
        return chatModel;
    }

    private ChatModel parseExllamav3(JSONObject modelObj) {
        ChatModel chatModel = new ChatModel();
        chatModel.setModelName(modelObj.getString("id"));
        JSONObject metaJSONObj = modelObj.getJSONObject("meta");
        chatModel.setContentLength(metaJSONObj.getInteger("n_ctx_train"));
        return chatModel;
    }

    private ChatModel parseVllmSglang(JSONObject modelObj) {
        ChatModel chatModel = new ChatModel();
        chatModel.setModelName(modelObj.getString("id"));
        chatModel.setContentLength(getIntField(modelObj, "max_model_len"));
        return chatModel;
    }

    private ChatModel parseLlamaCpp(JSONObject modelObj) {
        ChatModel chatModel = new ChatModel();
        chatModel.setModelName(modelObj.getString("id"));
        chatModel.setModelTypeEnum(ModelTypeEnum.LLAMACPP);
        int contentLength = DEFAULT_CONTENT_LENGTH;
        JSONObject meta = modelObj.getJSONObject("meta");
        if (meta != null) {
            Object ctxValue = meta.get("n_ctx_train");
            if (ctxValue == null) {
                ctxValue = meta.get("n_ctx");
            }
            contentLength = safeParseInt(ctxValue, DEFAULT_CONTENT_LENGTH);
        }
        chatModel.setContentLength(contentLength);
        return chatModel;
    }

    private ChatModel parseTgi(JSONObject modelObj) {
        ChatModel chatModel = new ChatModel();
        chatModel.setModelName(modelObj.getString("id"));
        chatModel.setModelTypeEnum(ModelTypeEnum.TGI);
        chatModel.setContentLength(getIntField(modelObj, "max_total_tokens"));
        return chatModel;
    }

    private ChatModel parseOllama(JSONObject modelObj, int contentLength) {
        ChatModel chatModel = new ChatModel();
        chatModel.setModelName(modelObj.getString("id"));
        chatModel.setModelTypeEnum(ModelTypeEnum.OLLAMA);
        chatModel.setContentLength(contentLength);
        return chatModel;
    }

    private Map<String, Integer> fetchOllamaContextLengths(OpenApiLLMConfig config, JSONArray dataArray) {
        List<String> modelNames = new ArrayList<>();
        for (int i = 0; i < dataArray.size(); i++) {
            modelNames.add(dataArray.getJSONObject(i).getString("id"));
        }
        Map<String, Integer> contextLengthMap = new HashMap<>();

        Map<String, Integer> psContextLengthMap = fetchOllamaContextLengthFromPs(config);
        if (psContextLengthMap != null) {
            contextLengthMap.putAll(psContextLengthMap);
        }

        List<String> missingModels = modelNames.stream()
                .filter(name -> !contextLengthMap.containsKey(name))
                .collect(Collectors.toList());
        if (!missingModels.isEmpty()) {
            Map<String, Integer> showContextLengthMap = fetchOllamaContextLengthsFromShow(config, missingModels);
            contextLengthMap.putAll(showContextLengthMap);
        }

        LOGGER.debug("ollama url={} resolved context lengths: {} total, {} from /api/ps, {} from /api/show",
                config.getBaseUrl(), contextLengthMap.size(),
                psContextLengthMap == null ? 0 : psContextLengthMap.size(),
                contextLengthMap.size() - (psContextLengthMap == null ? 0 : psContextLengthMap.size()));
        return contextLengthMap;
    }

    private Map<String, Integer> fetchOllamaContextLengthFromPs(OpenApiLLMConfig config) {
        Map<String, String> headerMap = buildHeaders(config);
        HttpRequest request = HttpUtil.createGet(config.getBaseUrl() + "/api/ps");
        request.addHeaders(headerMap);
        request.timeout(3000);
        try (HttpResponse response = request.execute()) {
            if (!response.isOk()) {
                LOGGER.warn("ollama /api/ps failed for url={}, status={}", config.getBaseUrl(), response.getStatus());
                return null;
            }
            String body = response.body();
            if (!JsonValidUtil.isValidJSONObjectType(body)) {
                return null;
            }
            JSONObject psResp = JSONObject.parseObject(body);
            JSONArray modelsArray = psResp.getJSONArray("models");
            if (modelsArray == null || modelsArray.isEmpty()) {
                return null;
            }
            Map<String, Integer> contextLengthMap = new HashMap<>();
            for (int i = 0; i < modelsArray.size(); i++) {
                JSONObject modelObj = modelsArray.getJSONObject(i);
                String modelName = modelObj.getString("name");
                if (StringUtils.isBlank(modelName)) {
                    continue;
                }
                int ctx = safeParseInt(modelObj.get("context_length"), DEFAULT_CONTENT_LENGTH);
                contextLengthMap.put(modelName, ctx);
            }
            return contextLengthMap;
        } catch (Exception e) {
            if (e instanceof ConnectException || e instanceof IORuntimeException || e instanceof HttpException) {
                return null;
            }
            LOGGER.error("ollama /api/ps url={} exception", config.getBaseUrl(), e);
            return null;
        }
    }

    private Map<String, Integer> fetchOllamaContextLengthsFromShow(OpenApiLLMConfig config, List<String> modelNames) {
        Map<String, Integer> contextLengthMap = new HashMap<>();
        try {
            List<Integer> contextLengthList = CompletableFutureUtil.asyncApplyAllOf(modelNames,
                    name -> fetchOllamaContextLength(config, name), commonAsyncExecutor).join();
            for (int i = 0; i < modelNames.size(); i++) {
                contextLengthMap.put(modelNames.get(i), contextLengthList.get(i));
            }
        } catch (Exception e) {
            LOGGER.error("fetch ollama context length url={} exception", config.getBaseUrl(), e);
        }
        return contextLengthMap;
    }

    private int fetchOllamaContextLength(OpenApiLLMConfig config, String modelName) {
        Map<String, String> headerMap = buildHeaders(config);
        JSONObject requestBody = new JSONObject();
        requestBody.put("name", modelName);

        HttpRequest request = HttpUtil.createPost(config.getBaseUrl() + "/api/show");
        request.addHeaders(headerMap);
        request.body(requestBody.toJSONString());
        request.contentType("application/json");
        request.timeout(3000);
        try (HttpResponse response = request.execute()) {
            if (!response.isOk()) {
                LOGGER.warn("ollama /api/show failed for model={}, url={}, status={}", modelName, config.getBaseUrl(), response.getStatus());
                return DEFAULT_CONTENT_LENGTH;
            }
            String body = response.body();
            if (!JsonValidUtil.isValidJSONObjectType(body)) {
                return DEFAULT_CONTENT_LENGTH;
            }
            JSONObject showResp = JSONObject.parseObject(body);
            JSONObject modelInfo = showResp.getJSONObject("model_info");
            if (modelInfo == null) {
                return DEFAULT_CONTENT_LENGTH;
            }
            for (Map.Entry<String, Object> entry : modelInfo.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith(".context_length")) {
                    return safeParseInt(entry.getValue(), DEFAULT_CONTENT_LENGTH);
                }
            }
        } catch (Exception e) {
            if (e instanceof ConnectException || e instanceof IORuntimeException || e instanceof HttpException) {
                // ignore
            } else {
                LOGGER.error("ollama /api/show url={} model={} exception", config.getBaseUrl(), modelName, e);
            }
        }
        return DEFAULT_CONTENT_LENGTH;
    }

    private ChatModel parseKtransformers(JSONObject modelObj) {
        ChatModel chatModel = new ChatModel();
        chatModel.setModelName(modelObj.getString("id"));
        chatModel.setModelTypeEnum(ModelTypeEnum.KTRANSFORMERS);
        chatModel.setContentLength(getIntField(modelObj, "max_model_len"));
        return chatModel;
    }

    private ChatModel parseOpenAi(JSONObject modelObj) {
        ChatModel chatModel = new ChatModel();
        chatModel.setModelName(modelObj.getString("id"));
        chatModel.setModelTypeEnum(ModelTypeEnum.OPENAI);
        chatModel.setContentLength(DEFAULT_CONTENT_LENGTH);
        return chatModel;
    }

    private boolean isOllamaServer(OpenApiLLMConfig config) {
        Map<String, String> headerMap = buildHeaders(config);
        HttpRequest request = HttpUtil.createGet(config.getBaseUrl() + "/api/tags");
        request.addHeaders(headerMap);
        request.timeout(3000);
        try (HttpResponse response = request.execute()) {
            if (response.isOk()) {
                String body = response.body();
                return JSONUtil.isTypeJSONObject(body) && JSONObject.parseObject(body).getJSONArray("models") != null;
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private int getIntField(JSONObject jsonObject, String fieldName) {
        return safeParseInt(jsonObject.get(fieldName), DEFAULT_CONTENT_LENGTH);
    }

    private int safeParseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            long longValue = ((Number) value).longValue();
            return longValue > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) longValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            try {
                double doubleValue = Double.parseDouble(value.toString());
                return doubleValue > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) doubleValue;
            } catch (NumberFormatException ex) {
                LOGGER.warn("invalid numeric value={}, use default={}", value, defaultValue);
                return defaultValue;
            }
        }
    }

    private JSONObject httpGet(String url, Map<String, String> headers,
                               AtomicReference<String> bodyRef, StringBuilder errorMsgBuilder) {
        HttpRequest request = HttpUtil.createGet(url);
        request.addHeaders(headers);
        request.timeout(3000);
        try {
            HttpResponse response = request.execute();
            try {
                if (response.getStatus() == HttpStatus.HTTP_UNAUTHORIZED || response.getStatus() == HttpStatus.HTTP_FORBIDDEN) {
                    errorMsgBuilder.append("API Key 无效或权限不足");
                    return null;
                }
                if (response.getStatus() == HttpStatus.HTTP_BAD_GATEWAY) {
                    errorMsgBuilder.append("服务暂时不可用 (Bad Gateway)");
                    return null;
                }
                if (!response.isOk()) {
                    LOGGER.error("failed to request url={}", url);
                    errorMsgBuilder.append("请求失败，HTTP 状态码: ").append(response.getStatus());
                    return null;
                }
                String body = response.body();
                bodyRef.set(body);
                if (!JsonValidUtil.isValidJSONObjectType(body)) {
                    LOGGER.error("open api url={} response is not valid JSON: {}", url, body);
                    errorMsgBuilder.append("响应格式错误，期望 JSON");
                    return null;
                }
                return JSONObject.parseObject(body);
            } finally {
                response.close();
            }
        } catch (Exception e) {
            if (e instanceof ConnectException || e instanceof IORuntimeException || e instanceof HttpException) {
                errorMsgBuilder.append("网络不可达，请检查 URL 配置");
            } else {
                LOGGER.error("request open api url={} exception", url, e);
                errorMsgBuilder.append(String.format("返回结果:%s,异常原因：%s ",
                        Optional.ofNullable(bodyRef.get()).orElse(StringUtils.EMPTY), e.getMessage()));
            }
        }
        return null;
    }

    private Map<String, String> buildHeaders(OpenApiLLMConfig config) {
        Map<String, String> headerMap = new HashMap<>();
        if (StringUtils.isNotBlank(config.getApiKey())) {
            headerMap.put("Authorization", String.format("Bearer %s", config.getApiKey()));
        }
        return headerMap;
    }

    @Override
    public ChatModel enrichWithFuncConfig(ChatModel chatModel) {
        ModelFuncConfig matchedConfig = modelFuncConfigManager.matchConfig(chatModel.getModelName());
        if (matchedConfig != null) {
            if (matchedConfig.getVisibility() != null) {
                chatModel.setVisibility(matchedConfig.getVisibility());
            }
            if (!chatModel.isAutoDetect() || Boolean.TRUE.equals(matchedConfig.getOverrideLocal())) {
                chatModel.setMulti(matchedConfig.getFunc());
                if (StringUtils.isNotBlank(matchedConfig.getThinkParamName())) {
                    ChatModel.ThinkConfig thinkConfig = new ChatModel.ThinkConfig();
                    thinkConfig.setParamName(matchedConfig.getThinkParamName());
                    thinkConfig.setThinkingLevel(matchedConfig.getThinkingLevel());
                    thinkConfig.setEnableThinking(matchedConfig.getEnableThinking());
                    thinkConfig.setEnableThinkingParamName(matchedConfig.getEnableThinkingParamName());
                    chatModel.setThinkConfig(thinkConfig);
                } else {
                    chatModel.setThinkConfig(null);
                }
            }
        }
        return chatModel;
    }
}
