package com.grw.xiaobai.hybrid.llm.utils;

import java.util.Map;

import org.apache.commons.collections4.MapUtils;

import com.google.common.collect.Maps;

/**
 * 线程本地上下文，调用结束后必须显式调用{@link #clear()}清空数据
 */
public class ThreadLocalContext {

    private static final ThreadLocal<Map<String, Object>> CONTEXT = InheritableThreadLocal.withInitial(Maps::newHashMap);

    public static void putAll(Map<String, Object> values) {
        CONTEXT.get().putAll(values);
    }

    @SuppressWarnings("unchecked")
    public static <T> T put(String key, T value) {
        return (T) CONTEXT.get().put(key, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        return (T) CONTEXT.get().get(key);
    }

    @SuppressWarnings("unchecked")
    public static <T> T remove(String key) {
        return (T) CONTEXT.get().remove(key);
    }

    public static void clear() {
        CONTEXT.get().clear();
    }

    public static Map<String, Object> getAll() {
        return MapUtils.unmodifiableMap(CONTEXT.get());
    }
}
