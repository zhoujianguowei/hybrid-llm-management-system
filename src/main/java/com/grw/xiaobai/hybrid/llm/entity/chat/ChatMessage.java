package com.grw.xiaobai.hybrid.llm.entity.chat;

import cn.hutool.core.lang.Pair;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

@Data
public class ChatMessage implements Serializable {
    public enum MessageStatus {
        INIT_BEFORE,
        INIT_FINISHED,
        THINKING,
        GENERATION,
        STOP,
        ERROR,
        FINISH;

        public static boolean isInRunning(MessageStatus status) {
            return INIT_BEFORE == status || INIT_FINISHED == status || THINKING == status || GENERATION == status;
        }
    }

    private String role;
    /**
     * 此轮对话对应的模型名称(assistant 消息使用, 历史数据可能为空)
     */
    private String modelName;
    private String content;
    private String thinkingContent;
    private Long timestamp;
    private ChatStats stats;
    private  MessageStatus status;
    private List<String> imageUrlList;
    private List<String> videoUrlList;
    private List<String> audioUrlList;
    private List<ChatMediaText> chatMediaTextList;
    private volatile boolean formatJson;

    public List<Pair<String, String>> generateMulPairResult() {
        List<Pair<String, String>> pairList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(imageUrlList)) {
            for (String imageUrl : imageUrlList) {
                pairList.add(new Pair<>("image_url", imageUrl));
            }
        }
        if (CollectionUtils.isNotEmpty(videoUrlList)) {
            for (String videoUrl : videoUrlList) {
                pairList.add(new Pair<>("video_url", videoUrl));
            }
        }
        if (CollectionUtils.isNotEmpty(audioUrlList)) {
            for (String audioUrl : audioUrlList) {
                pairList.add(new Pair<>("input_audio", audioUrl));
            }
        }
        return pairList;
    }
}