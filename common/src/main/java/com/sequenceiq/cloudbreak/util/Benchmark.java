package com.sequenceiq.cloudbreak.util;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;

public class Benchmark {

    private static final int MILLIS_1000 = 1000;

    private static final int SECONDS_60 = 60;

    private static final int SECONDS_30 = 30;

    private static final int SECONDS_15 = 15;

    private static final int SECONDS_5 = 5;

    private Benchmark() {
    }

    public static void measure(Runnable callback, Logger logger, String message, Object... params) {
        long start = System.currentTimeMillis();
        callback.run();
        logDuration(logger, "[MEASURE] " + message, start, params);
    }

    public static <T> T measure(Supplier<T> callback, Logger logger, String message, Object... params) {
        long start = System.currentTimeMillis();
        T resp = callback.get();
        logDuration(logger, "[MEASURE] " + message, start, params);
        return resp;
    }

    public static <T> T measureAndWarnIfLong(Supplier<T> callback, Logger logger, String message) {
        long start = System.currentTimeMillis();
        T result = callback.get();
        long duration = System.currentTimeMillis() - start;
        Stream.of(SECONDS_60, SECONDS_30, SECONDS_15, SECONDS_5)
                .filter(seconds -> duration > seconds * MILLIS_1000)
                .findFirst()
                .ifPresent(interval -> logger.warn("[MEASURE] {} duration was critical (>{}s) {}ms", message, interval, duration));
        return result;
    }

    public static <E extends Exception> void checkedMeasure(SingleCheckedRunnable<E> runnable, Logger logger, String message, Object... params) throws E {
        long start = System.currentTimeMillis();
        runnable.run();
        logDuration(logger, "[MEASURE] " + message, start, params);
    }

    public static <T, E extends Exception> T checkedMeasure(SingleCheckedSupplier<T, E> callback, Logger logger, String message, Object... params) throws E {
        long start = System.currentTimeMillis();
        T resp = callback.get();
        logDuration(logger, "[MEASURE] " + message, start, params);
        return resp;
    }

    public static <T, E extends Exception, X extends Exception> T multiCheckedMeasure(MultiCheckedSupplier<T, E, X> callback, Logger logger, String message,
            Object... params) throws E, X {
        long start = System.currentTimeMillis();
        T resp = callback.get();
        logDuration(logger, "[MEASURE] " + message, start, params);
        return resp;
    }

    private static void logDuration(Logger logger, String message, long start, Object[] params) {
        Object[] extParams = Stream.concat(Stream.of(System.currentTimeMillis() - start), Stream.of(params)).toArray();
        logger.debug(message, extParams);
    }

    @FunctionalInterface
    public interface SingleCheckedSupplier<T, E extends Exception> {
        T get() throws E;
    }

    @FunctionalInterface
    public interface SingleCheckedRunnable<E extends Exception> {
        void run() throws E;
    }

    @FunctionalInterface
    public interface MultiCheckedSupplier<T, E extends Exception, X extends Exception> {
        T get() throws E, X;
    }
}
