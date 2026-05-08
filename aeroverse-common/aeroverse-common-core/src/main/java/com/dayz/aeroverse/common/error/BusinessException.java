package com.dayz.aeroverse.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 携带业务错误码的运行时异常。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(int code, String message) {
        this(new LegacyErrorCode(code, message), message);
    }

    public int getCode() {
        return errorCode.code();
    }

    public HttpStatus getHttpStatus() {
        return errorCode.httpStatus();
    }

    private record LegacyErrorCode(int code, String message) implements ErrorCode {
        @Override
        public HttpStatus httpStatus() {
            return HttpStatus.BAD_REQUEST;
        }
    }
}
