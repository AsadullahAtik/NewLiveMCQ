package com.livemcq.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions thrown anywhere in a controller into the ApiError JSON.
 * Deliberately NO catch-all @ExceptionHandler(Exception.class): it would also swallow Spring's own
 * 404 / 405 / 415 exceptions and turn them into 500s. Unexpected errors fall through to Spring Boot's
 * default error response, which never includes a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> apiException(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiError.of(e.getStatus().value(), e.getCode(), e.getMessage()));
    }

    // @Valid failed: collect "field -> first message"
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ApiError body = new ApiError(java.time.Instant.now(), HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED", "Some fields are missing or invalid", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "BAD_REQUEST_BODY", "Request body is missing or is not valid JSON"));
    }
}
