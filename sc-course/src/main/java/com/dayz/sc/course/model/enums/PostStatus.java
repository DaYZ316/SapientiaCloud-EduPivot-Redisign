package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 状态枚举
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@RequiredArgsConstructor
public enum PostStatus {

    /**
     * 正常状态，帖子可见
     */
    NORMAL(0, "正常"),
    /**
     * 已删除状态，帖子已被删除
     */
    DELETED(1, "已删除"),
    /**
     * 审核中状态，帖子正在审核
     */
    UNDER_REVIEW(2, "审核中"),
    /**
     * 审核失败状态，帖子未通过审核
     */
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
