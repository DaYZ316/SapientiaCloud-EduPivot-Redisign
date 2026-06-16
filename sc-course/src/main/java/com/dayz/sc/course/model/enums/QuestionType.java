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
public enum QuestionType {

    /**
     * 单选题，只能选择一个答案
     */
    SINGLE_CHOICE(0, "单选题"),
    /**
     * 多选题，可以选择多个答案
     */
    MULTI_CHOICE(1, "多选题"),
    /**
     * 判断题，判断对错
     */
    TRUE_FALSE(2, "判断题"),
    /**
     * 填空题，填写空白处答案
     */
    FILL_BLANK(3, "填空题"),
    /**
     * 简答题，需要文字作答
     */
    SHORT_ANSWER(4, "简答题");

    private final int code;
    private final String description;

    public static QuestionType fromCode(int code) {
        for (QuestionType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST);
    }
}
