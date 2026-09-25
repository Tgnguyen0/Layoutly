package com.tgnguyen.layoutlybe.config;

import com.tgnguyen.layoutlybe.exception.FigmaRateLimitException;
import com.tgnguyen.layoutlybe.exception.PayloadTooLargeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FigmaRateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(FigmaRateLimitException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "RATE_LIMITED");
        body.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        body.put("retryAt", java.time.Instant.now().plusSeconds(ex.getRetryAfterSeconds()).toString());
        body.put("message", ex.getMessage());
        if (ex.getPlanTier() != null) body.put("planTier", ex.getPlanTier());
        if (ex.getRateLimitType() != null) body.put("rateLimitType", ex.getRateLimitType());
        if (ex.getUpgradeUrl() != null) body.put("upgradeUrl", ex.getUpgradeUrl());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleMissingToken(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Map<String, String>> handleFigmaError(WebClientResponseException ex) {
        String message = ex.getStatusCode().value() == 414
                ? "Yeu cau asset Figma qua dai. Backend can chia danh sach node thanh cac lo nho."
                : "Figma API loi HTTP " + ex.getStatusCode().value() + ": " + ex.getStatusText();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", message));
    }

    @ExceptionHandler({PayloadTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, String>> handlePayloadTooLarge(Exception ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", ex.getMessage() == null ? "Payload qua lon." : ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeError(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", message));
    }
}
