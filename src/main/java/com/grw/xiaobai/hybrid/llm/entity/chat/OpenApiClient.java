package com.grw.xiaobai.hybrid.llm.entity.chat;

import cn.hutool.core.lang.Pair;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.grw.xiaobai.hybrid.llm.constant.ThinkingConstants;
import com.grw.xiaobai.hybrid.llm.utils.image.ImageProcessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import com.grw.xiaobai.hybrid.llm.utils.JsonValidUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Data
public class OpenApiClient {
    private String baseUrl;
    private String modelName;
    private boolean stream;
    private int requestTimeoutMillis = (int) TimeUnit.MINUTES.toMillis(5);
    private String apiKey;
    private String systemPrompt;
    private ChatHistory chatHistory = new ChatHistory();
    private Call currentCall;
    private ChatThinkConfig chatThinkConfig;
    private ChatRuntimeConfig chatRuntimeConfig = new ChatRuntimeConfig();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");


    public OpenApiClient(String modelName, String baseUrl) {
        this.modelName = modelName;
        this.baseUrl = baseUrl;
    }

    public void setSystemPrompt(String systemPrompt) {
        chatHistory.setSystemPrompt(systemPrompt);
    }

    public OpenApiClient(String modelName, String baseUrl, ChatHistory chatHistory) {
        this(modelName, baseUrl);
        this.chatHistory = chatHistory;
    }

    public String sendRequestWithMessage(String userPrompt, boolean formatJson) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(userPrompt);
        chatHistory.addUserMessage(chatMessage);
        return sendRequest(chatHistory, formatJson);
    }

    public String sendRequestWithMessageAndImage(String message, String imagePath, boolean formatJson) {
        try {
            String base64EncodeImageData = ImageProcessor.resizeCompressAndEncode(imagePath, 1536, 0.9f);
            chatHistory.addMultiModalUserMessage(message, base64EncodeImageData);
            return sendRequest(chatHistory, formatJson);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String sendRequest(ChatHistory chatHistory, boolean formatJson) {
        return sendRequest(chatHistory.getHistory(), formatJson);
    }

    public String sendRequest(List<Map<String, Object>> messages, boolean formatJson) {
        String jsonPayload = buildRequestPayload(messages, formatJson, false);
        String requestUrl = String.format("%s/v1/chat/completions", baseUrl);
        log.info("发送请求到 MLLM 服务器，包含 {} 条历史消息...", messages.size());

        try {
            HttpRequest request = HttpRequest.post(requestUrl);
            if (StringUtils.isNotBlank(apiKey)) {
                request.header("Authorization", String.format("Bearer %s", apiKey));
            }
            request.header(Header.CONTENT_TYPE, "application/json;charset=utf-8")
                    .body(jsonPayload)
                    .timeout(requestTimeoutMillis);
            HttpResponse response = request.execute();
            if (!response.isOk()) {
                String errorBody = response.body();
                log.error("请求失败: {} - {}", response.getStatus(), errorBody);
                throw new IOException("Unexpected code " + response.getStatus());
            }

            String responseBody = response.body();
            JSONObject responseJson = JSONObject.parseObject(responseBody);
            return responseJson.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();
        } catch (Exception e) {
            log.error("请求失败", e);
            throw new RuntimeException(e);
        }
    }


    public void sendStreamRequestWithMessage(ChatMessage chatMessage, BiConsumer<Throwable, String> biChunkConsumer,
                                             BiConsumer<Throwable, String> biThinkingConsumer, BiConsumer<Throwable, OpenApiStats> apiStatsBiConsumer) {
        List<Pair<String, String>> attachPairList = chatMessage.generateMulPairResult();
        if (CollectionUtils.isNotEmpty(attachPairList) || CollectionUtils.isNotEmpty(chatMessage.getChatMediaTextList())) {
            chatHistory.addMultiUserMessage(chatMessage, attachPairList, chatMessage.getChatMediaTextList());
        } else {
            chatHistory.addUserMessage(chatMessage);
        }
        sendStreamRequest(chatHistory.getHistory(), chatMessage.isFormatJson(), biChunkConsumer, biThinkingConsumer, apiStatsBiConsumer);
    }


    public void cancelCurrentCall() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            log.info("已取消当前请求");
        }
    }

    private void detectError(String line, String requestUrl) {
        if (JsonValidUtil.isValidJSONObjectType(line)) {
            JSONObject jsonObject = JSONObject.parseObject(line);
            if (jsonObject.containsKey("error")) {
                log.error("open api request error||requestUrl={}||response={}", requestUrl, line);
                throw new RuntimeException(Optional.ofNullable(jsonObject.getJSONObject("error").getString("message")).orElse("openapi服务请求失败"));
            }
        }
    }

    public void sendStreamRequest(List<Map<String, Object>> messages, boolean formatJson, BiConsumer<Throwable, String> biChunkConsumer,
                                  BiConsumer<Throwable, String> biThinkingConsumer, BiConsumer<Throwable, OpenApiStats> openApiStatsBiConsumer) {
        String jsonPayload = buildRequestPayload(messages, formatJson, true);
        String requestUrl = String.format("%s/v1/chat/completions", baseUrl);
        log.info("发送流式请求到 MLLM 服务器，包含 {} 条历史消息...", messages.size());

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.MINUTES)
                .build();

        Request.Builder requestBuilder = new Request.Builder()
                .url(requestUrl)
                .post(RequestBody.create(JSON_MEDIA_TYPE, jsonPayload));

        if (StringUtils.isNotBlank(apiKey)) {
            requestBuilder.addHeader("Authorization", String.format("Bearer %s", apiKey));
        }
        requestBuilder.addHeader("Content-Type", "application/json;charset=utf-8");

        Request request = requestBuilder.build();

        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = new Exception[1];
        currentCall = client.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) {
                    log.info("流式请求已被取消（正常中止）");
                } else {
                    error[0] = e;
                    log.error("流式请求异常", e);
                }
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) {
                String line;
                AtomicInteger atomicStage = new AtomicInteger(1);
                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        latch.countDown();
                        return;
                    }
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8));
                    StringBuilder contentBuilder = new StringBuilder();
                    StringBuilder thinkingBuilder = new StringBuilder();
                    BiConsumer<Throwable, String> enhancedBiConsumer = (th, v) -> {
                        if (th == null && StringUtils.isNotBlank(v)) {
                            contentBuilder.append(v);
                            atomicStage.set(2);
                        }
                        biChunkConsumer.accept(th, v);
                    };
                    BiConsumer<Throwable, String> enhancedBiThinkingConsumer = (th, v) -> {
                        if (th == null && StringUtils.isNotBlank(v)) {
                            thinkingBuilder.append(v);
                        }
                        biThinkingConsumer.accept(th, v);
                    };
                    while ((line = reader.readLine()) != null) {
                        detectError(line, requestUrl);
                        if (line.startsWith("data: ")) {
                            String jsonStr = line.substring(6);
                            detectError(jsonStr, requestUrl);
                            if (jsonStr.equals("[DONE]")) {
                                chatHistory.addAssistantMessage(contentBuilder.toString(),thinkingBuilder.toString());
                                break;
                            }
                            processStreamChunk(jsonStr, enhancedBiConsumer, enhancedBiThinkingConsumer, openApiStatsBiConsumer);
                        }
                    }
                } catch (Exception exception) {
                    if (call.isCanceled()) {
                        log.info("读取流时请求被取消");
                    } else {
                        log.error("unexpected stream exception", exception);
                        if (atomicStage.get() == 1) {
                            biThinkingConsumer.accept(exception, null);
                        } else {
                            biChunkConsumer.accept(exception, null);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            latch.await();
            if (error[0] != null) {
                throw new RuntimeException("Streaming request failed", error[0]);
            }
        } catch (InterruptedException e) {
            log.error("流式请求被中断", e);
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, Object> constructJsonMap(String part, Object val) {
        String[] parts = part.split("\\.");
        return constructJsonMap(parts, 0, val);
    }

    private static Map<String, Object> constructJsonMap(String[] parts, int currentIndex, Object val) {
        Map<String, Object> map = new HashMap<>();
        if (parts.length - 1 == currentIndex) {
            map.put(parts[currentIndex], val);
            return map;
        }
        map.put(parts[currentIndex], constructJsonMap(parts, currentIndex + 1, val));
        return map;
    }

    private static Map<String, Object> combineMap(Map<String, Object> mp1, Map<String, Object> mp2) {
        Map<String, Object> mergeMap = Maps.newHashMap();
        mergeMap.putAll(mp1);
        for (Map.Entry<String, Object> entry : mp2.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (!mergeMap.containsKey(key)) {
                mergeMap.put(key, val);
            } else if (val instanceof Map && mergeMap.get(key) instanceof Map) {
                mergeMap.put(key, combineMap((Map<String, Object>) val, (Map<String, Object>) mergeMap.get(key)));
            }
        }
        return mergeMap;
    }

    private String buildRequestPayload(List<Map<String, Object>> messages, boolean formatJson, boolean isStream) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        List<Map<String, Object>> copyMessageList = new ArrayList<>();
        for (Map<String, Object> mp : messages) {
            Map<String, Object> copyMp = new LinkedHashMap<>(mp);
            String reasoningContent = (String) copyMp.get("reasoning_content");
            if (chatRuntimeConfig.isAttachReasoningContent() && "assistant".equals(mp.get("role")) && StringUtils.isNotBlank(reasoningContent)) {
                copyMp.put("reasoning_content", reasoningContent);
            } else {
                copyMp.remove("reasoning_content");
            }
            copyMessageList.add(copyMp);
        }
        payload.put("messages", copyMessageList);
        if (chatRuntimeConfig.getTemperature() != null) {
            payload.put("temperature", chatRuntimeConfig.getTemperature());
        }
        if (chatRuntimeConfig.getTopP() != null) {
            payload.put("top_p", chatRuntimeConfig.getTopP());
        }
        // maxTokens 为 0 表示采用服务端默认值，不携带 max_tokens
        if (chatRuntimeConfig.getMaxTokens() != null && chatRuntimeConfig.getMaxTokens() > 0) {
            payload.put("max_tokens", chatRuntimeConfig.getMaxTokens());
        }
        if (chatRuntimeConfig.getPresencePenalty() != null) {
            payload.put("presence_penalty", chatRuntimeConfig.getPresencePenalty());
        }
        if (chatRuntimeConfig.getFrequencyPenalty() != null) {
            payload.put("frequency_penalty", chatRuntimeConfig.getFrequencyPenalty());
        }
        payload.put("stream", isStream);
        JSONObject streamOptionsJSONObject = new JSONObject();
        streamOptionsJSONObject.put("include_usage", true);
        payload.put("stream_options", streamOptionsJSONObject);
        if (formatJson) {
            Map<String, String> responseFormat = new LinkedHashMap<>();
            responseFormat.put("type", "json_object");
            payload.put("response_format", responseFormat);
        }
        if (chatThinkConfig != null) {
            if (StringUtils.isNotBlank(chatThinkConfig.getEnableThinkingParamName())) {
                payload.putAll(constructJsonMap(chatThinkConfig.getEnableThinkingParamName(), chatThinkConfig.getEnableThinking()));
                if (!chatThinkConfig.getEnableThinkingParamName().startsWith(ThinkingConstants.PARAM_CHAT_TEMPLATE_KWARGS_PREFIX)) {
                    payload.putAll(constructJsonMap(ThinkingConstants.PARAM_CHAT_TEMPLATE_KWARGS_PREFIX + chatThinkConfig.getEnableThinkingParamName(), chatThinkConfig.getEnableThinking()));
                }
            }
            if (StringUtils.isNotBlank(chatThinkConfig.getParamName())) {
                payload.putAll(constructJsonMap(chatThinkConfig.getParamName(), chatThinkConfig.getThinkingLevel()));
                if (!chatThinkConfig.getParamName().startsWith(ThinkingConstants.PARAM_CHAT_TEMPLATE_KWARGS_PREFIX)) {
                    Map<String, Object> objectMap = constructJsonMap(ThinkingConstants.PARAM_CHAT_TEMPLATE_KWARGS_PREFIX + chatThinkConfig.getParamName(), chatThinkConfig.getThinkingLevel());
                    payload = combineMap(payload, objectMap);
                }
            }
        }
        return JSONObject.toJSONString(payload);
    }

    private void processStreamChunk(String jsonStr, BiConsumer<Throwable, String> biChunkConsumer,
                                    BiConsumer<Throwable, String> biThinkingConsumer, BiConsumer<Throwable, OpenApiStats> openApiStatsBiConsumer) {
        int stage = 1;
        try {
            JSONObject json = JSONObject.parseObject(jsonStr);
            if (json.containsKey("usage") || json.containsKey("timings")) {
                openApiStatsBiConsumer.accept(null, JSONObject.parseObject(jsonStr, OpenApiStats.class));
            }
            JSONArray choicesArray = json.getJSONArray("choices");
            if (CollectionUtils.isEmpty(choicesArray)) {
                return;
            }
            JSONObject delta = choicesArray
                    .getJSONObject(0)
                    .getJSONObject("delta");
            if (delta != null) {
                String content = delta.getString("content");
                if (content != null && !content.isEmpty() && biChunkConsumer != null) {
                    biChunkConsumer.accept(null, content);
                    stage = 2;
                }
                String thinking = delta.getString("reasoning_content");
                if (thinking != null && !thinking.isEmpty() && biThinkingConsumer != null) {
                    biThinkingConsumer.accept(null, thinking);
                }
            }
        } catch (Exception e) {
            log.warn("解析流式响应失败: {}", jsonStr, e);
            openApiStatsBiConsumer.accept(e, null);
            if (stage == 1) {
                biThinkingConsumer.accept(e, null);
            } else {
                biChunkConsumer.accept(e, null);
            }
        }
    }
}
