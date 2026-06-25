package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Class barrage response.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public record ClassBarrageVO(
        @JsonProperty UUID id,
        @JsonProperty UUID sessionId,
        @JsonProperty UUID senderId,
        @JsonProperty String senderDisplayName,
        @JsonProperty String senderAvatarUrl,
        @JsonProperty String senderRoleLabel,
        @JsonProperty String content,
        @JsonProperty Instant sentAt
) {
}
