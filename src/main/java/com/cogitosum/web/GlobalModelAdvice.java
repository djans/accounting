package com.cogitosum.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes commonly-needed request data to all Thymeleaf templates.
 * <p>
 * The implicit {@code #request} expression utility object is no longer
 * available by default in Spring/Thymeleaf, so the current request URI is
 * published as a regular model attribute instead.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("requestURI")
    public String requestURI(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (query == null) {
            return uri;
        }
        // Remove existing lang parameter to avoid duplication
        query = java.util.Arrays.stream(query.split("&"))
                .filter(p -> !p.startsWith("lang="))
                .collect(java.util.stream.Collectors.joining("&"));
        
        return query.isEmpty() ? uri : uri + "?" + query;
    }

    @ModelAttribute("currentUser")
    public Authentication currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName()) ? authentication : null;
    }
}
