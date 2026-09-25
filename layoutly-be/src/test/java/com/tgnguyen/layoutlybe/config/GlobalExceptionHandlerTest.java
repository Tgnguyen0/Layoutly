package com.tgnguyen.layoutlybe.config;

import com.tgnguyen.layoutlybe.exception.FigmaRateLimitException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void returnsStructuredRateLimitResponse() {
        var response = new GlobalExceptionHandler().handleRateLimit(new FigmaRateLimitException(45));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody()).containsEntry("code", "RATE_LIMITED")
                .containsEntry("retryAfterSeconds", 45L);
    }
}
