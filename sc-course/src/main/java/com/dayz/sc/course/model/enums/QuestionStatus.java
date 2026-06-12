package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuestionStatus {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布"),
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
