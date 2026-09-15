package com.grw.xiaobai.hybrid.llm.utils.converter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ma.glasnost.orika.converter.BidirectionalConverter;

/**
 * 字段名映射配置，指定源/目标属性对的引用及可选的双向转换器
 *
 * @param <IN>  源类型
 * @param <OUT> 目标类型
 * @param <IP>  源属性类型
 * @param <OP>  目标属性类型
 */
@Getter
@AllArgsConstructor
public class FieldNameMapping<IN, OUT, IP, OP> {

    /** 源属性 getter 的方法引用 */
    private final SerializableFieldRef<IN, IP> sourceFieldRef;
    /** 目标属性 getter 的方法引用 */
    private final SerializableFieldRef<OUT, OP> targetFieldRef;
    /** 可选的双向转换器，为空时按字段名直接映射 */
    private final BidirectionalConverter<?, ?> converter;
}
