package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 加入课堂会话请求
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public record JoinClassSessionRequest(
        @NotNull BigDecimal x,
        @NotNull BigDecimal y,
        BigDecimal z,
        Integer seatIndex
) {
}
