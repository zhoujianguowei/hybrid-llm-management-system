package com.grw.xiaobai.hybrid.llm.utils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

/**
 * CompletableFuture 异步批量执行封装
 */
public class CompletableFutureUtil {

    /**
     * 异步批量运行任务，全部完成后返回
     */
    public static CompletableFuture<Void> asyncRunAllOf(List<Runnable> taskList, Executor executor) {
        return CompletableFuture.allOf(taskList.stream()
                .map(task -> CompletableFuture.runAsync(task, executor))
                .toArray(CompletableFuture[]::new));
    }

    /**
     * 对列表中每个元素异步执行 consumer，全部完成后返回
     */
    public static <V> CompletableFuture<Void> asyncRunAllOf(List<V> sourceList, Consumer<V> consumer, Executor executor) {
        List<Runnable> taskList = sourceList.stream()
                .map(item -> (Runnable) () -> consumer.accept(item))
                .collect(Collectors.toList());
        return asyncRunAllOf(taskList, executor);
    }

    /**
     * 异步批量获取执行结果，按入参顺序返回列表
     */
    public static <T> CompletableFuture<List<T>> asyncApplyAllOf(List<Supplier<T>> supplierList, Executor executor) {
        List<CompletableFuture<T>> futureList = supplierList.stream()
                .map(supplier -> CompletableFuture.supplyAsync(supplier, executor))
                .collect(Collectors.toList());
        List<T> resultList = Lists.newArrayList();
        return CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]))
                .whenComplete((ret, throwable) -> {
                    if (throwable == null) {
                        futureList.forEach(future -> resultList.add(future.join()));
                    }
                })
                .thenApplyAsync(ret -> resultList, executor);
    }

    /**
     * 对列表中每个元素异步执行 function，按入参顺序返回列表，使用公共线程池
     */
    public static <V, T> CompletableFuture<List<T>> asyncApplyAllOf(List<V> sourceList, Function<V, T> function) {
        return asyncApplyAllOf(sourceList, function, ForkJoinPool.commonPool());
    }

    /**
     * 对列表中每个元素异步执行 function，按入参顺序返回列表
     */
    public static <V, T> CompletableFuture<List<T>> asyncApplyAllOf(List<V> sourceList, Function<V, T> function, Executor executor) {
        List<Supplier<T>> supplierList = sourceList.stream()
                .map(item -> (Supplier<T>) () -> function.apply(item))
                .collect(Collectors.toList());
        return asyncApplyAllOf(supplierList, executor);
    }
}
