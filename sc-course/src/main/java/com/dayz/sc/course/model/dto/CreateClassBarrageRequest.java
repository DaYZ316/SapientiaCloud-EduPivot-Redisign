package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to send a class barrage message.
 */
public record CreateClassBarrageRequest(
        @NotBlank @Size(max = 300) String content
) {}
