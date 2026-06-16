package com.dayz.sc.course.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

/**
 * Runtime status for a class session.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Getter
@RequiredArgsConstructor
public enum ClassSessionStatus {

    /**
     * 备课中，课程尚未发布
     */
    PREPARING(0, "备课中"),
    /**
     * 即将开课，课程已发布但未到开始时间
     */
    UPCOMING(1, "即将开课"),
    /**
     * 正在上课，课程进行中
     */
    LIVE(2, "正在上课"),
    /**
     * 已下课，课程已结束
     */
    FINISHED(3, "已下课");

    private final int code;
    private final String description;

    public static ClassSessionStatus calculate(Instant publishedAt, Instant startAt, Instant endAt, Instant now) {
        if (publishedAt == null) {
            return PREPARING;
        }
        if (now.isBefore(startAt)) {
            return UPCOMING;
        }
        if (now.isBefore(endAt)) {
            return LIVE;
        }
        return FINISHED;
    }
}
