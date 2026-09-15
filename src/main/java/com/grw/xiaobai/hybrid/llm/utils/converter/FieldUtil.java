package com.grw.xiaobai.hybrid.llm.utils.converter;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * 属性工具类，封装了常见的获取指定的类的所有属性，通过方法引用获取属性名称等
 */
public class FieldUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(FieldUtil.class);

    private static final int MAX_CACHE_SIZE = Integer.parseInt(System.getProperty("guava.cached.class.threshold", "1000"));
    private static final String IS_PREFIX = "is";
    private static final String GET_PREFIX = "get";
    private static final String SET_PREFIX = "set";
    /** 可序列化 lambda 编译后生成的方法名，调用它可以获得 {@link SerializedLambda} */
    private static final String LAMBDA_WRITE_REPLACE_METHOD = "writeReplace";

    /** 类 -> 可访问（非静态、非继承私有）属性集合缓存 */
    private static final LoadingCache<Class<?>, Set<Field>> ACCESSIBLE_FIELD_CACHE =
            CacheBuilder.newBuilder().maximumSize(MAX_CACHE_SIZE)
                    .build(CacheLoader.from(FieldUtil::loadAccessibleFieldSet));
    /** 类 -> 全部属性集合缓存 */
    private static final LoadingCache<Class<?>, Set<Field>> ALL_FIELD_CACHE =
            CacheBuilder.newBuilder().maximumSize(MAX_CACHE_SIZE)
                    .build(CacheLoader.from(ReflectionUtils::getAllFields));
    /** 类 -> 属性名到属性映射缓存 */
    private static final Map<Class<?>, Map<String, Field>> FIELD_NAME_MAP_CACHE = Maps.newConcurrentMap();
    /** 类 -> 属性名到 {@link FieldAccessor} 映射缓存 */
    private static final LoadingCache<Class<?>, Map<String, FieldAccessor>> FIELD_ACCESSOR_CACHE =
            CacheBuilder.newBuilder().maximumSize(MAX_CACHE_SIZE)
                    .removalListener(FieldUtil::onAccessorCacheRemoval)
                    .build(CacheLoader.from(FieldUtil::loadFieldAccessorMap));
    /** lambda 实现类 -> 序列化信息缓存 */
    private static final Map<Class<?>, SerializedLambda> SERIALIZED_LAMBDA_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static Set<Field> getAllAccessibleFields(Class<?> clazz) {
        return ACCESSIBLE_FIELD_CACHE.getUnchecked(clazz);
    }

    public static Set<Field> getAllFields(Class<?> clazz) {
        return ALL_FIELD_CACHE.getUnchecked(clazz);
    }

    public static Map<String, FieldAccessor> getFieldAccessorMap(Class<?> clazz) {
        return FIELD_ACCESSOR_CACHE.getUnchecked(clazz);
    }

    public static Map<String, Field> getFieldNameMap(Class<?> clazz) {
        return FIELD_NAME_MAP_CACHE.computeIfAbsent(clazz, key -> getAllFields(key).stream()
                .collect(Collectors.toMap(Field::getName, Function.identity(), (first, second) -> first)));
    }

    /**
     * 通过属性 getter 的方法引用解析属性名称，如 {@code User::getName} -> {@code name}
     */
    public static <T, P> String getFieldPropertyName(SerializableFieldRef<T, ? extends P> fieldRef) {
        SerializedLambda lambda = getSerializedLambda(fieldRef);
        if (lambda == null) {
            throw new RuntimeException("lambda not exists");
        }
        String methodName = lambda.getImplMethodName();
        String prefix = null;
        if (methodName.startsWith(GET_PREFIX)) {
            prefix = GET_PREFIX;
        } else if (methodName.startsWith(IS_PREFIX)) {
            prefix = IS_PREFIX;
        }
        if (prefix == null || methodName.length() == prefix.length()) {
            LOGGER.error("not a valid get method||fn={}||methodName={}", fieldRef.getClass().getName(), methodName);
            throw new IllegalArgumentException("not a valid get method functional reference");
        }
        return StringUtils.uncapitalize(methodName.replace(prefix, StringUtils.EMPTY));
    }

    /**
     * 通过属性 getter 的方法引用获取对应的 {@link Field}
     */
    public static <T, P> Field getPropertyField(Class<T> clazz, SerializableFieldRef<T, ? extends P> fieldRef) {
        String propertyName = getFieldPropertyName(fieldRef);
        FieldAccessor fieldAccessor = getFieldAccessorMap(clazz).get(propertyName);
        if (fieldAccessor == null) {
            LOGGER.error("target class={} not exists {} property", clazz.getName(), propertyName);
            throw new RuntimeException("such field not exists");
        }
        return fieldAccessor.getField();
    }

    /**
     * 读取目标对象指定属性的值
     */
    public static <T, P> P readField(T target, SerializableFieldRef<T, ? extends P> fieldRef, boolean forceAccess) {
        if (target == null) {
            return null;
        }
        Class<T> clazz = (Class<T>) target.getClass();
        Field field = getPropertyField(clazz, fieldRef);
        try {
            return (P) FieldUtils.readField(field, target, forceAccess);
        } catch (IllegalAccessException e) {
            LOGGER.error("failed to read {} {} property", clazz.getName(), field.getName(), e);
            throw new RuntimeException(e);
        }
    }

    public static <T, P> P readField(T target, SerializableFieldRef<T, P> fieldRef) {
        return readField(target, fieldRef, true);
    }

    /**
     * 获取属性上指定注解的取值
     */
    public static <T, P_IN, A extends Annotation, P_OUT> P_OUT getPropertyAnnotationVal(
            Class<T> clazz, SerializableFieldRef<T, ? extends P_IN> fieldRef, Class<A> annotationClass,
            Function<A, ? extends P_OUT> annotationValueGetter) {
        Field field = getPropertyField(clazz, fieldRef);
        A annotation = AnnotationUtils.getAnnotation(field, annotationClass);
        if (annotation == null) {
            LOGGER.error("target annotation={} non exists||", annotationClass.getName());
            throw new RuntimeException("annotation type non exists");
        }
        return annotationValueGetter.apply(annotation);
    }

    private static Set<Field> loadAccessibleFieldSet(Class<?> clazz) {
        Set<Field> fieldSet = ReflectionUtils.getAllFields(clazz);
        Set<Field> declaredFieldSet = Arrays.stream(clazz.getDeclaredFields()).collect(Collectors.toSet());
        fieldSet.removeIf(field -> {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                return true;
            }
            // 父类的私有字段无法访问，需要剔除
            return !declaredFieldSet.contains(field) && Modifier.isPrivate(modifiers);
        });
        return fieldSet;
    }

    private static Map<String, FieldAccessor> loadFieldAccessorMap(Class<?> clazz) {
        Map<String, FieldAccessor> fieldName2AccessorMap = Maps.newHashMap();
        for (Field field : getAllAccessibleFields(clazz)) {
            FieldAccessor fieldAccessor = constructFieldAccessor(field);
            if (fieldAccessor == null) {
                LOGGER.warn("failed to acquire property get、set method||propertyName={}||simpleClassName={}",
                        field.getName(), field.getDeclaringClass().getSimpleName());
                continue;
            }
            fieldName2AccessorMap.put(field.getName(), fieldAccessor);
        }
        return fieldName2AccessorMap;
    }

    private static FieldAccessor constructFieldAccessor(Field field) {
        String fieldName = field.getName();
        Class<?> declaringClass = field.getDeclaringClass();
        Class<?> fieldType = field.getType();

        Method getMethod = resolveGetMethod(declaringClass, fieldName, fieldType);
        if (getMethod == null) {
            return null;
        }
        Method setMethod = resolveSetMethod(declaringClass, fieldName, fieldType);
        if (setMethod == null) {
            return null;
        }

        FieldAccessor fieldAccessor = new FieldAccessor();
        fieldAccessor.setField(field);
        fieldAccessor.setGetMethod(getMethod);
        fieldAccessor.setSetMethod(setMethod);
        return fieldAccessor;
    }

    private static Method resolveGetMethod(Class<?> declaringClass, String fieldName, Class<?> fieldType) {
        List<String> candidateNames = Lists.newArrayList(GET_PREFIX + StringUtils.capitalize(fieldName));
        if (fieldType == boolean.class) {
            candidateNames.add(IS_PREFIX + StringUtils.capitalize(fieldName));
            if (fieldName.startsWith(IS_PREFIX)) {
                candidateNames.add(fieldName);
            }
        }
        for (String candidateName : candidateNames) {
            Method method = MethodUtils.getAccessibleMethod(declaringClass, candidateName);
            if (method != null && Objects.equals(fieldType, method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    private static Method resolveSetMethod(Class<?> declaringClass, String fieldName, Class<?> fieldType) {
        List<String> candidateNames = Lists.newArrayList(SET_PREFIX + StringUtils.capitalize(fieldName));
        if (fieldType == boolean.class && fieldName.startsWith(IS_PREFIX)) {
            candidateNames.add(SET_PREFIX + StringUtils.capitalize(fieldName.substring(IS_PREFIX.length())));
        }
        for (String candidateName : candidateNames) {
            Method method = MethodUtils.getAccessibleMethod(declaringClass, candidateName, fieldType);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private static void onAccessorCacheRemoval(RemovalNotification<Class<?>, Map<String, FieldAccessor>> notification) {
        if (notification.getCause() == RemovalCause.SIZE) {
            ACCESSIBLE_FIELD_CACHE.invalidate(notification.getKey());
            ALL_FIELD_CACHE.invalidate(notification.getKey());
            FIELD_NAME_MAP_CACHE.remove(notification.getKey());
        }
    }

    private static SerializedLambda getSerializedLambda(Serializable lambdaInstance) {
        SerializedLambda cached = SERIALIZED_LAMBDA_CACHE.get(lambdaInstance.getClass());
        if (cached != null) {
            return cached;
        }
        synchronized (lambdaInstance.getClass()) {
            cached = SERIALIZED_LAMBDA_CACHE.get(lambdaInstance.getClass());
            if (cached != null) {
                return cached;
            }
            try {
                Method writeReplace = lambdaInstance.getClass().getDeclaredMethod(LAMBDA_WRITE_REPLACE_METHOD);
                writeReplace.setAccessible(true);
                SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(lambdaInstance);
                SERIALIZED_LAMBDA_CACHE.put(lambdaInstance.getClass(), lambda);
                return lambda;
            } catch (Exception e) {
                LOGGER.error("failed to acquire serialized lambda||function={}||exception={}",
                        lambdaInstance.getClass().getName(), e);
                throw new RuntimeException(e);
            }
        }
    }
}
