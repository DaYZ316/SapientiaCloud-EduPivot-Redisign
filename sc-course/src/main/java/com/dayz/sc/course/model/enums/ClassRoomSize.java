package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 3D classroom size level.
 */
@Getter
@RequiredArgsConstructor
public enum ClassRoomSize {

    SMALL(0, "小型教室"),
    MEDIUM(1, "中型教室"),
    LARGE(2, "大型教室"),
    XLARGE(3, "超大型教室");

    private final int code;
    private final String description;

    public static ClassRoomSize fromCode(int code) {
        for (ClassRoomSize size : values()) {
            if (size.code == code) {
                return size;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid classroom size");
    }
}
