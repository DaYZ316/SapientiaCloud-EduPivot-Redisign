package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * QuestionDifficulty 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@RequiredArgsConstructor
public enum QuestionDifficulty {

    EASY(1, "简单"),
    MEDIUM(2, "中等"),
    HARD(3, "困难");

    private final int code;
    private final String description;

    public static QuestionDifficulty fromCode(int code) {
        for (QuestionDifficulty difficulty : values()) {
            if (difficulty.code == code) {
                return difficulty;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST);
    }
}
