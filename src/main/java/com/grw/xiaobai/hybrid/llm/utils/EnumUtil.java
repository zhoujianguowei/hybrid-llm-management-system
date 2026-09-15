package com.grw.xiaobai.hybrid.llm.utils;

import java.util.Objects;
import java.util.function.Function;

/**
 * 通用枚举工具类，能够通过枚举的任意属性获取对应的枚举值，或者通过枚举属性指定的值获取该属性关联的
 * 其它属性
 */
public class EnumUtil {

    /**
     * 根据枚举的指定属性获取实际对应的枚举值
     *
     * @param enumClass        枚举类
     * @param propertyGetter   属性对应的取值方法
     * @param propertyValue    属性值
     * @param defaultEnum      未匹配时返回的默认枚举值
     * @param <E>              枚举类
     * @param <P>              枚举类对应属性
     * @return 对应的枚举值
     */
    public static <E extends Enum<E>, P> E getEnumByPropertyValue(
            Class<E> enumClass, Function<E, P> propertyGetter, P propertyValue, E defaultEnum) {
        for (E enumConstant : enumClass.getEnumConstants()) {
            if (Objects.equals(propertyGetter.apply(enumConstant), propertyValue)) {
                return enumConstant;
            }
        }
        return defaultEnum;
    }

    public static <E extends Enum<E>, P> E getEnumByPropertyValue(
            Class<E> enumClass, Function<E, P> propertyGetter, P propertyValue) {
        return getEnumByPropertyValue(enumClass, propertyGetter, propertyValue, null);
    }

    /**
     * 获取枚举属性对应的映射属性
     *
     * @param enumClass        枚举类
     * @param propertyGetter   源属性取值方法
     * @param propertyValue    源属性值
     * @param targetGetter     目标属性取值方法
     * @param <E>              枚举类
     * @param <SP>             源属性类型
     * @param <TP>             目标属性类型
     * @return 对应枚举属性值
     */
    public static <E extends Enum<E>, SP, TP> TP getEnumCorrespondingProperty(
            Class<E> enumClass,
            Function<E, SP> propertyGetter,
            SP propertyValue,
            Function<E, TP> targetGetter) {
        E enumInstance = getEnumByPropertyValue(enumClass, propertyGetter, propertyValue);
        if (enumInstance == null) {
            return null;
        }
        return targetGetter.apply(enumInstance);
    }
}
