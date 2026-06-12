package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ForumType {

    DISCUSSION(0, "讨论"),
    Q_AND_A(1, "问答"),
    HOMEWORK(2, "作业"),
    ANNOUNCEMENT(3, "公告");

    private final int code;
    private final String description;

    public static ForumType fromCode(int code) {
        for (ForumType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST);
    }
}
