package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ForumStatus {

    NORMAL(0, "正常"),
    CLOSED(1, "关闭"),
    MAINTENANCE(2, "维护中");

    private final int code;
    private final String description;

    public static ForumStatus fromCode(int code) {
        for (ForumStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST);
    }
}
