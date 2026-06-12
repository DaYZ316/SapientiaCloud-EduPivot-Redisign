package com.dayz.sc.notification.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 未读通知数量视图对象。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public record UnreadCountVO(
        @JsonProperty("total") long total,
        @JsonProperty("system") long system,
        @JsonProperty("teaching") long teaching
) {
}
