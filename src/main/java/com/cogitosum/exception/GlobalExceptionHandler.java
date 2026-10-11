package com.cogitosum.exception;

import jakarta.servlet.http.HttpServletRequest;
import com.cogitosum.config.LoggingCategory;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(HttpServletRequest request) {
        log.debug("Resource not found while processing {} {}", request.getMethod(), request.getRequestURI());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not found");
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleDisconnectedClient(AsyncRequestNotUsableException e,
                                                          HttpServletRequest request) {
        log.debug("Client disconnected while processing {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    
    @ExceptionHandler(BillingException.class)
    public ResponseEntity<ErrorResponse> handleBillingException(BillingException e,
                                                                  HttpServletRequest request) {
        try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.ERROR)) {
            log.error("Billing error while processing {} {}", request.getMethod(), request.getRequestURI(), e);
        }
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            e.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.ERROR)) {
            log.error("Unhandled error while processing {} {}", request.getMethod(), request.getRequestURI(), e);
        }
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An error occurred: " + e.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
