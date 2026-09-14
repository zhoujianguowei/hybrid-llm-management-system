package com.grw.xiaobai.hybrid.llm.utils.converter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FieldAccessor {
    private Field field;
    private Method getMethod;
    private Method setMethod;
}
