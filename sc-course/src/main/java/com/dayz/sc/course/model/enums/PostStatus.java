package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 状态枚举。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@RequiredArgsConstructor
public enum PostStatus {

    NORMAL(0, "正常"),
    DELETED(1, "已删除"),
    UNDER_REVIEW(2, "审核中"),
    REVIEW_FAILED(3, "审核失败");

    private final int code;
    private final String description;

    public static PostStatus fromCode(int code) {
        for (PostStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST);
    }
}
