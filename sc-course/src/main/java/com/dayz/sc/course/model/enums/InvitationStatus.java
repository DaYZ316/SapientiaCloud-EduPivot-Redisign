package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;

/**
 * 状态枚举
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public enum InvitationStatus {

    /**
     * 待处理状态，邀请已发送等待响应
     */
    PENDING(0, "待处理"),
    /**
     * 已接受状态，受邀者已接受邀请
     */
    ACCEPTED(1, "已接受"),
    /**
     * 已拒绝状态，受邀者已拒绝邀请
     */
    DECLINED(2, "已拒绝"),
    /**
     * 已撤回状态，邀请已被撤回
     */
    WITHDRAWN(3, "已撤回");

    private final int code;
    private final String description;

    InvitationStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static InvitationStatus fromCode(int code) {
        for (InvitationStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid invitation status: " + code);
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
