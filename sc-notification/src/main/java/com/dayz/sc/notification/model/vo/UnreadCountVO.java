package com.dayz.sc.notification.model.vo;

/**
 * 未读通知数量视图对象。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public record UnreadCountVO(
        long total,
        long system,
        long teaching
) {
}
