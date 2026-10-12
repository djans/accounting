package com.cogitosum.config;

import org.slf4j.MDC;

public final class LoggingCategory {

    public static final String USER_ACTIVITY = "USER_ACTIVITY";
    public static final String ERROR = "ERROR";
    public static final String INTERNAL_PROCESS = "INTERNAL_PROCESS";

    private static final String MDC_KEY = "logCategory";

    private LoggingCategory() {
    }

    public static Scope open(String category) {
        String previousCategory = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, category);
        return () -> {
            if (previousCategory == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousCategory);
            }
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
