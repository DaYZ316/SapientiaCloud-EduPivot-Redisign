package com.dayz.sc.common.error;

import org.springframework.http.HttpStatus;

/**
 * 描述系统错误码及其默认 HTTP 状态映射
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public interface ErrorCode {
    /**
     * 获取业务错误码，用于客户端和日志识别具体错误类型
     *
     * @return 业务错误码
     */
    int code();

    /**
     * 获取错误码对应的默认提示信息
     *
     * @return 默认提示信息
     */
    String message();

    /**
     * 获取错误码对应的默认 HTTP 状态
     *
     * @return HTTP 状态
     */
    HttpStatus httpStatus();
}
