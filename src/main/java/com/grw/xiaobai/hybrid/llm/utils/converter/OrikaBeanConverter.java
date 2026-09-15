package com.grw.xiaobai.hybrid.llm.utils.converter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import javassist.ClassPool;
import javassist.LoaderClassPath;
import lombok.AllArgsConstructor;
import ma.glasnost.orika.MapperFacade;
import ma.glasnost.orika.MapperFactory;
import ma.glasnost.orika.OrikaSystemProperties;
import ma.glasnost.orika.converter.BidirectionalConverter;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import ma.glasnost.orika.impl.generator.JavassistCompilerStrategy;
import ma.glasnost.orika.metadata.ClassMapBuilder;

/**
 * 基于 orika 的 bean 转换工具类，转换工厂按（源类、目标类、忽略字段）缓存
 */
public class OrikaBeanConverter {

    private static final Map<MapperFactoryCacheKey, MapperFactory> MAPPER_FACTORY_CACHE = Maps.newConcurrentMap();
    /** 已完成首次映射（触发字节码生成）的 源类:目标类 对 */
    private static final Set<String> COMPILED_PAIR_SET = Sets.newConcurrentHashSet();

    static {
        System.setProperty(OrikaSystemProperties.WRITE_SOURCE_FILES, "false");
        System.setProperty(OrikaSystemProperties.WRITE_CLASS_FILES, "false");
        ClassPool classPool = ClassPool.getDefault();
        classPool.appendClassPath(new LoaderClassPath(OrikaBeanConverter.class.getClassLoader()));
    }

    /**
     * 将源对象转换为目标类新实例
     */
    public static <IN, OUT> OUT convert(Class<OUT> targetClass, IN source) {
        return convert(targetClass, source, Collections.emptySet());
    }

    public static <IN, OUT> OUT convert(Class<OUT> targetClass, IN source, FieldNameMapping... fieldMappings) {
        if (source == null) {
            return null;
        }
        return convert((Class<IN>) source.getClass(), targetClass, source, fieldMappings);
    }

    public static <IN, OUT> OUT convert(Class<OUT> targetClass, IN source, String[] excludeFieldNames) {
        return convert(targetClass, source, Sets.newHashSet(excludeFieldNames));
    }

    public static <IN, OUT> OUT convert(Class<OUT> targetClass, IN source, Set<String> excludeFieldNames,
                                        FieldNameMapping... fieldMappings) {
        if (source == null) {
            return null;
        }
        return convert((Class<IN>) source.getClass(), targetClass, source, excludeFieldNames, fieldMappings);
    }

    public static <IN, OUT> OUT convert(Class<IN> sourceClass, Class<OUT> targetClass, IN source,
                                        FieldNameMapping... fieldMappings) {
        return convert(sourceClass, targetClass, source, Collections.emptySet(), fieldMappings);
    }

    /**
     * 核心转换入口：首次转换在锁内完成以串行化字节码生成，后续直接映射
     */
    public static <IN, OUT> OUT convert(Class<IN> sourceClass, Class<OUT> targetClass, IN source,
                                        Set<String> excludeFieldNames, FieldNameMapping... fieldMappings) {
        if (source == null) {
            return null;
        }
        ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(OrikaBeanConverter.class.getClassLoader());
            MapperFactory mapperFactory = generateMapperFactory(sourceClass, targetClass, excludeFieldNames, fieldMappings);
            MapperFacade mapperFacade = mapperFactory.getMapperFacade();
            String pairKey = sourceClass.getName() + ":" + targetClass.getName();
            if (!COMPILED_PAIR_SET.contains(pairKey)) {
                synchronized (pairKey.intern()) {
                    OUT firstResult = mapperFacade.map(source, targetClass);
                    COMPILED_PAIR_SET.add(pairKey);
                    return firstResult;
                }
            }
            return mapperFacade.map(source, targetClass);
        } finally {
            Thread.currentThread().setContextClassLoader(originClassLoader);
        }
    }

    /**
     * 将源对象的属性合并到已有目标对象，目标对象中的非空属性不会被源对象的 null 覆盖
     */
    public static <IN, OUT> void mergeKeepNonNull(OUT target, IN source) {
        mergeKeepNonNull(target, source, Collections.emptySet());
    }

    public static <IN, OUT> void mergeKeepNonNull(OUT target, IN source, String... excludeFieldNames) {
        mergeKeepNonNull(target, source, Sets.newHashSet(excludeFieldNames));
    }

    public static <IN, OUT> void convert(OUT target, IN source, FieldNameMapping... fieldMappings) {
        mergeKeepNonNull(target, source, Collections.emptySet(), fieldMappings);
    }

    public static <IN, OUT> void mergeKeepNonNull(OUT target, IN source, Set<String> excludeFieldNames,
                                                  FieldNameMapping... fieldMappings) {
        if (source == null || target == null) {
            return;
        }
        Map<String, Field> targetFieldMap = FieldUtil.getFieldNameMap(target.getClass());
        Map<String, Object> targetNonNullValues = snapshotNonNullFields(target, targetFieldMap);

        ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(OrikaBeanConverter.class.getClassLoader());
            MapperFactory mapperFactory = generateMapperFactory(source.getClass(), target.getClass(),
                    excludeFieldNames, fieldMappings);
            mapperFactory.getMapperFacade().map(source, target);
            restoreNonNullFields(target, targetFieldMap, targetNonNullValues);
        } finally {
            Thread.currentThread().setContextClassLoader(originClassLoader);
        }
    }

    /**
     * 批量转换列表元素为目标类实例
     */
    public static <IN, OUT> List<OUT> convertList(List<IN> sourceList, Class<OUT> targetClass) {
        return convertList(sourceList, targetClass, Collections.emptySet());
    }

    public static <IN, OUT> List<OUT> convertList(List<IN> sourceList, Class<OUT> targetClass,
                                                  Set<String> excludeFieldNames, FieldNameMapping... fieldMappings) {
        if (CollectionUtils.isEmpty(sourceList)) {
            return new ArrayList<>();
        }
        Class<IN> sourceClass = (Class<IN>) sourceList.get(0).getClass();
        return convertList(sourceList, item -> convert(sourceClass, targetClass, item, excludeFieldNames, fieldMappings));
    }

    public static <IN, OUT> List<OUT> convertList(List<IN> sourceList, Function<IN, OUT> converter) {
        if (CollectionUtils.isEmpty(sourceList)) {
            return new ArrayList<>();
        }
        List<OUT> targetList = new ArrayList<>(sourceList.size());
        for (IN sourceItem : sourceList) {
            targetList.add(converter.apply(sourceItem));
        }
        return targetList;
    }

    /**
     * 在已有 MapperFactory 上注册源/目标类的字段映射与忽略字段
     */
    @SafeVarargs
    public static <IN, OUT> MapperFactory registerClassMapping(MapperFactory mapperFactory, Class<IN> sourceClass,
                                                               Class<OUT> targetClass, Set<String> excludeFieldNames,
                                                               FieldNameMapping<IN, OUT, ?, ?>... fieldMappings) {
        ClassMapBuilder<IN, OUT> mapBuilder = mapperFactory.classMap(sourceClass, targetClass);
        if (ArrayUtils.isNotEmpty(fieldMappings)) {
            for (FieldNameMapping<IN, OUT, ?, ?> fieldMapping : fieldMappings) {
                String sourceFieldName = FieldUtil.getFieldPropertyName(fieldMapping.getSourceFieldRef());
                String targetFieldName = FieldUtil.getFieldPropertyName(fieldMapping.getTargetFieldRef());
                BidirectionalConverter<?, ?> converter = fieldMapping.getConverter();
                if (converter == null) {
                    mapBuilder.field(sourceFieldName, targetFieldName);
                    continue;
                }
                String converterId = converter.getClass().getName();
                mapperFactory.getConverterFactory().registerConverter(converterId, converter);
                mapBuilder.fieldMap(sourceFieldName, targetFieldName).converter(converterId).add();
            }
        }
        if (CollectionUtils.isNotEmpty(excludeFieldNames)) {
            for (String excludeFieldName : excludeFieldNames) {
                mapBuilder.exclude(excludeFieldName);
            }
        }
        mapBuilder.byDefault().register();
        return mapperFactory;
    }

    public static <IN, OUT> MapperFactory registerClassMapping(MapperFactory mapperFactory, Class<IN> sourceClass,
                                                               Class<OUT> targetClass, FieldNameMapping... fieldMappings) {
        return registerClassMapping(mapperFactory, sourceClass, targetClass, Collections.emptySet(), fieldMappings);
    }

    private static <IN, OUT> MapperFactory generateMapperFactory(Class<IN> sourceClass, Class<OUT> targetClass,
                                                                 Set<String> excludeFieldNames,
                                                                 FieldNameMapping... fieldMappings) {
        MapperFactoryCacheKey cacheKey = new MapperFactoryCacheKey(sourceClass, targetClass,
                Optional.ofNullable(excludeFieldNames).orElse(Collections.emptySet()));
        return MAPPER_FACTORY_CACHE.computeIfAbsent(cacheKey, key ->
                registerClassMapping(new DefaultMapperFactory.Builder()
                                .compilerStrategy(new JavassistCompilerStrategy()).build(),
                        sourceClass, targetClass, excludeFieldNames, fieldMappings)
        );
    }

    private static <OUT> Map<String, Object> snapshotNonNullFields(OUT target, Map<String, Field> targetFieldMap) {
        Map<String, Object> nonNullValues = Maps.newHashMap();
        for (Map.Entry<String, Field> entry : targetFieldMap.entrySet()) {
            try {
                Object fieldValue = FieldUtils.readField(entry.getValue(), target, true);
                if (fieldValue != null) {
                    nonNullValues.put(entry.getKey(), fieldValue);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return nonNullValues;
    }

    private static <OUT> void restoreNonNullFields(OUT target, Map<String, Field> targetFieldMap,
                                                   Map<String, Object> targetNonNullValues) {
        for (Map.Entry<String, Field> entry : targetFieldMap.entrySet()) {
            try {
                Object fieldValue = FieldUtils.readField(entry.getValue(), target, true);
                if (fieldValue == null && targetNonNullValues.containsKey(entry.getKey())) {
                    FieldUtils.writeField(entry.getValue(), target, targetNonNullValues.get(entry.getKey()), true);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * MapperFactory 缓存 key：源类 + 目标类 + 忽略字段集合
     */
    @AllArgsConstructor
    private static class MapperFactoryCacheKey {
        private final Class<?> sourceClass;
        private final Class<?> targetClass;
        private final Set<String> excludeFieldNames;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MapperFactoryCacheKey that = (MapperFactoryCacheKey) o;
            return Objects.equals(sourceClass, that.sourceClass)
                    && Objects.equals(targetClass, that.targetClass)
                    && SetUtils.isEqualSet(excludeFieldNames, that.excludeFieldNames);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceClass, targetClass, excludeFieldNames);
        }
    }
}
