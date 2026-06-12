package com.dayz.sc.notification.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通知类型枚举。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {
    SYSTEM(1, "系统公告"),
    TEACHING(2, "教学通知");

    private final int code;
    private final String description;

    public static NotificationType fromCode(int code) {
        for (NotificationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST, "无效的通知类型: " + code);
    }
}
