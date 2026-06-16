package com.dayz.sc.course.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

/**
 * Runtime status for a class session.
 */
@Getter
@RequiredArgsConstructor
public enum ClassSessionStatus {

    PREPARING(0, "备课中"),
    UPCOMING(1, "即将开课"),
    LIVE(2, "正在上课"),
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
