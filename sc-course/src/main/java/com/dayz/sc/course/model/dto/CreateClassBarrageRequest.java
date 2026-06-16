package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送课堂弹幕消息请求
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public record CreateClassBarrageRequest(
        @NotBlank @Size(max = 300) String content
) {
}
