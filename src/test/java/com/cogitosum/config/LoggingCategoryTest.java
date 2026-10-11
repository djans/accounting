package com.cogitosum.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoggingCategoryTest {

    @Test
    void restoresPreviousCategoryAfterScopeCloses() {
        MDC.put("logCategory", LoggingCategory.INTERNAL_PROCESS);

        try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.USER_ACTIVITY)) {
            assertEquals(LoggingCategory.USER_ACTIVITY, MDC.get("logCategory"));
        }

        assertEquals(LoggingCategory.INTERNAL_PROCESS, MDC.get("logCategory"));
        MDC.remove("logCategory");
    }

    @Test
    void removesCategoryWhenNoPreviousValueExists() {
        MDC.remove("logCategory");

        try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.ERROR)) {
            assertEquals(LoggingCategory.ERROR, MDC.get("logCategory"));
        }

        assertNull(MDC.get("logCategory"));
    }
}
