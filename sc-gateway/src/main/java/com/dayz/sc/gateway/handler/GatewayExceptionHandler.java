package com.dayz.sc.gateway.handler;

import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpServerErrorException;

/**
 * GatewayExceptionHandler.
 *
 * @author DaYZ
 */
@Slf4j
@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final String MISSING_INSTANCE_PREFIX = "Unable to find instance for ";

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<@NonNull ApiResponse<@NonNull Void>> handleServiceUnavailable(
            HttpServerErrorException exception) {
        String message = exception.getStatusText();
        if (exception.getStatusCode().value() != HttpStatus.SERVICE_UNAVAILABLE.value()
                || !message.startsWith(MISSING_INSTANCE_PREFIX)) {
            throw exception;
        }

        String serviceId = message.substring(MISSING_INSTANCE_PREFIX.length());
        log.warn("No available instance for service: {}", serviceId);

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail(ErrorCodes.SERVICE_UNAVAILABLE));
    }
}
