package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 类型枚举
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@RequiredArgsConstructor
public enum QuestionBankType {

    /**
     * 练习题库，用于日常练习
     */
    PRACTICE(0, "练习"),
    /**
     * 考试题库，用于正式考试
     */
    EXAM(1, "考试"),
    /**
     * 作业题库，用于课后作业
     */
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
