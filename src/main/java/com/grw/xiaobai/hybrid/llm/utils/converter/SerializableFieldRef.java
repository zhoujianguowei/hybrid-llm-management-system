package com.grw.xiaobai.hybrid.llm.utils.converter;

import java.io.Serializable;

/**
 * 属性函数接口，通过方法引用获取对象属性名称信息
 *
 * @param <T> 目标类
 * @param <P> 目标类属性
 */
@FunctionalInterface
public interface SerializableFieldRef<T, P> extends Serializable {

    /**
     * 实际为目标类属性的 getter 方法引用
     *
     * @param target 目标对象
     * @return 属性值
     */
    P apply(T target);
}
