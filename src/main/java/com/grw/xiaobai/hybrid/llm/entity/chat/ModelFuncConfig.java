package com.grw.xiaobai.hybrid.llm.entity.chat;

import com.grw.xiaobai.hybrid.llm.enums.ModelVisibilityEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ModelFuncConfig implements Serializable {
    private String id;
    private String type;
    private String regex;
    private int func;
    private ModelVisibilityEnum visibility;
    private Integer order;
    private Long createTime;
    private Long updateTime;
    private String thinkParamName;
    private List<String> thinkingLevel;
    private Boolean enableThinking;
    private String enableThinkingParamName;
    private Boolean overrideLocal = false;

}
