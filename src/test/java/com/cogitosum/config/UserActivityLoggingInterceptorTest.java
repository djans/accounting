package com.cogitosum.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cogitosum.service.UserShortIdService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserActivityLoggingInterceptorTest {

    @Test
    void logsSuccessfulInvoicePdfDownloadsAsUserActivity() throws Exception {
        String username = "customer@example.test";
        UserShortIdService shortIdService = mock(UserShortIdService.class);
        when(shortIdService.getOrCreate(username)).thenReturn("CUS001");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, "n/a", List.of()));

        Logger logger = (Logger) LoggerFactory.getLogger(UserActivityLoggingInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/invoices/42/pdf");
            request.setServletPath("/invoices/42/pdf");
            MockHttpServletResponse response = new MockHttpServletResponse();
            new UserActivityLoggingInterceptor(shortIdService)
                    .afterCompletion(request, response, this, null);

            ILoggingEvent event = appender.list.stream()
                    .filter(log -> log.getFormattedMessage().contains("Invoice PDF downloaded"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("USER_ACTIVITY", event.getMDCPropertyMap().get("logCategory"));
            assertTrue(event.getFormattedMessage().contains("actor=CUS001"));
            assertTrue(event.getFormattedMessage().contains("/invoices/42/pdf"));
            assertTrue(event.getFormattedMessage().contains("status=200"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            SecurityContextHolder.clearContext();
        }
    }
}
