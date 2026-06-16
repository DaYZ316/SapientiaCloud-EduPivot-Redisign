package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request to join a class session.
 */
public record JoinClassSessionRequest(
        @NotNull BigDecimal x,
        @NotNull BigDecimal y,
        BigDecimal z
) {}
