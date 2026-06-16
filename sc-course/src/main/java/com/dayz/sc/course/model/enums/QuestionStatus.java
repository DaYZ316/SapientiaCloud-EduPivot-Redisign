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
public enum QuestionStatus {

    /**
     * 草稿状态，题目尚未发布
     */
    DRAFT(0, "草稿"),
    /**
     * 已发布状态，题目可用于考试或练习
     */
    PUBLISHED(1, "已发布"),
    /**
     * 已禁用状态，题目不再可用
     */
    DISABLED(2, "已禁用");

    private final int code;
    private final String description;

    public static QuestionStatus fromCode(int code) {
        for (QuestionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST);
    }
}
