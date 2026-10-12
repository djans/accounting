package com.cogitosum.config;

import com.cogitosum.service.UserShortIdService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

public class UserActivityLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UserActivityLoggingInterceptor.class);
    private final UserShortIdService shortIdService;

    public UserActivityLoggingInterceptor(UserShortIdService shortIdService) {
        this.shortIdService = shortIdService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        String method = request.getMethod();
        boolean mutation = "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
        int status = response.getStatus();
        if (exception != null || status >= HttpServletResponse.SC_BAD_REQUEST) {
            try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.ERROR)) {
                log.warn("HTTP request failed: actor={} method={} path={} status={}",
                        actor(authentication, authenticated),
                        method, request.getRequestURI(), status);
            }
        } else if (mutation && authenticated) {
            try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.USER_ACTIVITY)) {
                log.info("User operation completed: actor={} method={} path={} status={}",
                        actor(authentication, true), method, request.getRequestURI(), status);
            }
        }
    }

    private String actor(Authentication authentication, boolean authenticated) {
        return authenticated ? shortIdService.getOrCreate(authentication.getName()) : "anonymous";
    }
}
