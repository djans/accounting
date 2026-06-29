package com.cogitosum.web;

import jakarta.servlet.http.HttpServletRequest;
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
        return request.getRequestURI();
    }
}
