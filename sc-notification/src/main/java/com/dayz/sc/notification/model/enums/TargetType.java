package com.dayz.sc.notification.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通知目标类型枚举。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Getter
@RequiredArgsConstructor
public enum TargetType {
    ALL(0, "全员"),
    USER(1, "指定用户"),
    CLASS(2, "指定班级");

    private final int code;
    private final String description;

    public static TargetType fromCode(int code) {
        for (TargetType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST, "无效的目标类型: " + code);
    }
}
