package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuestionBankType {

    PRACTICE(0, "练习"),
    EXAM(1, "考试"),
    HOMEWORK(2, "作业");

    private final int code;
    private final String description;

    public static QuestionBankType fromCode(int code) {
        for (QuestionBankType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST);
    }
}
