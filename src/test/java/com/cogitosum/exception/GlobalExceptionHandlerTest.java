package com.cogitosum.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void returnsNotFoundForMissingStaticResources() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/h2-console");

        var response = handler.handleNotFound(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
        assertEquals("Not found", response.getBody().getMessage());
    }
}
