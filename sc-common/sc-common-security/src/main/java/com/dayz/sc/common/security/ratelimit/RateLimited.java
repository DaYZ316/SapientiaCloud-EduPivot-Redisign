package com.dayz.sc.common.security.ratelimit;

import java.lang.annotation.*;

/**
 * 接口限流注解。
 * <p>
 * 标注在 Controller 方法上，基于客户端 IP 进行滑动窗口限流
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    /**
     * 时间窗口内最大请求数。
     */
    int maxRequests() default 10;

    /**
     * 时间窗口大小（秒）。
     */
    int windowSeconds() default 60;
}
