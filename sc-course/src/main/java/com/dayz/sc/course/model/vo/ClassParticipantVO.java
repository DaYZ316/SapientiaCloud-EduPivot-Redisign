package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Class session participant response.
 */
public record ClassParticipantVO(
        @JsonProperty UUID id,
        @JsonProperty UUID sessionId,
        @JsonProperty UUID userId,
        @JsonProperty Integer role,
        @JsonProperty BigDecimal x,
        @JsonProperty BigDecimal y,
        @JsonProperty BigDecimal z,
        @JsonProperty Instant joinedAt
) {}
