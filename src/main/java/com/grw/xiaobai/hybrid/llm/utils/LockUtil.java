package com.grw.xiaobai.hybrid.llm.utils;

import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;

public class LockUtil {
    public static <T> void lockRun(Function<T, Lock> lockFunction, Consumer<T> consumer, T t) {
        Lock lock = lockFunction.apply(t);
        lock.lock();
        try {
            consumer.accept(t);
        } finally {
            lock.unlock();
        }
    }
}
