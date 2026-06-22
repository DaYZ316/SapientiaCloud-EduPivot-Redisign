package com.dayz.sc.course.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Runtime status for the one-way class live stream.
 *
 * @author DaYZ
 * @since 2026-06-21
 */
@Getter
@RequiredArgsConstructor
public enum ClassLiveStatus {

    NOT_STARTED(0, "未开播"),
    LIVE(1, "直播中"),
    PAUSED(2, "已暂停"),
    ENDED(3, "已结束");

    private final int code;
    private final String description;

    public static ClassLiveStatus fromCode(Integer code) {
        if (code == null) {
            return NOT_STARTED;
        }
        for (ClassLiveStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return NOT_STARTED;
    }
}
