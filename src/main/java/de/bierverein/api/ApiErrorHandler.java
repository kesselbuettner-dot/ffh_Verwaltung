package de.bierverein.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/** Returns intentional validation reasons to the PWA instead of only "Bad Request". */
@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException exception) {
        String detail = exception.getReason();
        if (detail == null || detail.isBlank()) detail = exception.getStatusCode().toString();
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(detail, Instant.now()));
    }

    public record ApiError(String detail, Instant timestamp) {}
}
