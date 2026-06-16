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
public enum ChapterStatus {

    /**
     * 草稿状态，章节尚未发布
     */
    DRAFT(0, "草稿"),
    /**
     * 已发布状态，章节对学生可见
     */
    PUBLISHED(1, "已发布");

    private final int code;
    private final String description;

    public static ChapterStatus fromCode(int code) {
        for (ChapterStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST);
    }
}
