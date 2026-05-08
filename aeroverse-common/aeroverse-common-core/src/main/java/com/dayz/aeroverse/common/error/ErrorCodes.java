package com.dayz.aeroverse.common.error;

import org.springframework.http.HttpStatus;

/**
 * 系统统一错误码。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public enum ErrorCodes implements ErrorCode {
    /**
     * 请求处理成功。
     */
    SUCCESS(0, "success", HttpStatus.OK),
    /**
     * 请求参数不合法或格式错误。
     */
    BAD_REQUEST(40000, "请求参数错误", HttpStatus.BAD_REQUEST),
    /**
     * 请求未完成认证或认证凭证无效。
     */
    UNAUTHORIZED(40100, "未认证", HttpStatus.UNAUTHORIZED),
    /**
     * 当前用户无权访问目标资源。
     */
    FORBIDDEN(40300, "无访问权限", HttpStatus.FORBIDDEN),
    /**
     * 请求访问的资源不存在。
     */
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    /**
     * 系统内部异常。
     */
    SYSTEM_ERROR(50000, "系统异常", HttpStatus.INTERNAL_SERVER_ERROR),
    /**
     * Google 登录流程失败。
     */
    GOOGLE_LOGIN_FAILED(40101, "Google 登录失败", HttpStatus.UNAUTHORIZED),
    /**
     * GitHub 登录流程失败。
     */
    GITHUB_LOGIN_FAILED(40102, "GitHub 登录失败", HttpStatus.UNAUTHORIZED);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCodes(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
