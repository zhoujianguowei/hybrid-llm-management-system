package com.grw.xiaobai.hybrid.llm.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 思考模式相关常量。
 * 关闭思考的关键字为 {@link #NO_THINK} 与 {@link #OFF_VALUE}（归一为关闭，不下发思考参数）；
 * 开启思考的关键字为 {@link #ON_VALUE}。
 */
public final class ThinkingConstants {

    private ThinkingConstants() {
    }

    /** 关闭思考的等级标识（off 关键字） */
    public static final String NO_THINK = "no_think";
    public static final String LEVEL_NONE = "none";
    /** enable_thinking 风格布尔参数：关闭值 */
    public static final String OFF_VALUE = "false";
    /** enable_thinking 风格布尔参数：开启值 */
    public static final String ON_VALUE = "true";

    /** 思考档位（强度从低到高） */
    public static final String LEVEL_LOW = "low";
    public static final String LEVEL_MINIMAL = "minimal";
    public static final String LEVEL_MEDIUM = "medium";
    public static final String LEVEL_HIGH = "high";
    public static final String LEVEL_XHIGH = "xhigh";
    public static final String LEVEL_MAX = "max";

    /** 支持的思考参数名 */
    public static final String PARAM_ENABLE_THINKING = "enable_thinking";
    public static final String PARAM_REASONING_EFFORT = "reasoning_effort";
    /** reasoning_strength 参数名（纯强度档位：low/medium/high/xhigh，无关闭档） */
    public static final String PARAM_REASONING_STRENGTH = "reasoning_strength";
    /** thinking 参数名（布尔型，值 think/no_think） */
    public static final String PARAM_THINKING = "thinking";
    /** thinking_mode 参数名（三态：adaptive / enabled / disabled） */
    public static final String PARAM_THINKING_MODE = "thinking_mode";
    public static final String PARAM_CHAT_TEMPLATE_KWARGS_PREFIX = "chat_template_kwargs.";

    /** thinking_mode 的三态值 */
    public static final String THINKING_MODE_ADAPTIVE = "adaptive";
    public static final String THINKING_MODE_ENABLED = "enabled";
    public static final String THINKING_MODE_DISABLED = "disabled";

    /** 关闭思考的关键字集合（no_think / false / disabled 均表示关闭） */
    public static final Set<String> OFF_LEVELS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(NO_THINK, LEVEL_NONE, OFF_VALUE, THINKING_MODE_DISABLED)));

    /** GGUF 规范思考档位（强度从低到高，关闭档使用 {@link #NO_THINK}） */
    public static final List<String> CANONICAL_LEVELS = Collections.unmodifiableList(
            Arrays.asList(NO_THINK,LEVEL_NONE,LEVEL_MINIMAL, LEVEL_LOW, LEVEL_MEDIUM, LEVEL_HIGH, LEVEL_XHIGH, LEVEL_MAX));

    /** reasoning_strength 思考强度档位（强度从低到高，无关闭档） */
    public static final List<String> REASONING_STRENGTH_LEVELS = Collections.unmodifiableList(
            Arrays.asList( LEVEL_LOW, LEVEL_MEDIUM, LEVEL_HIGH, LEVEL_XHIGH));

    /** thinking_mode 的三态值列表（从低到高：disabled < adaptive < enabled） */
    public static final List<String> THINKING_MODE_VALUES = Collections.unmodifiableList(
            Arrays.asList(THINKING_MODE_DISABLED, THINKING_MODE_ADAPTIVE, THINKING_MODE_ENABLED));
}
