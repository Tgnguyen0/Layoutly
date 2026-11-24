package com.tgnguyen.layoutlybe.exception;

import org.springframework.http.HttpStatus;

/** Loi nghiep vu co ma HTTP ro rang (401, 404, 409, 429...). GlobalExceptionHandler tra ve {"error": message}. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
