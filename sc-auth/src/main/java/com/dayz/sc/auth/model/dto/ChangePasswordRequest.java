package com.dayz.sc.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Current user password change or initial password setup request.
 *
 * @param currentPassword existing local password, required only when one is already configured
 * @param newPassword     new local password
 * @author DaYZ
 * @since 2026-06-26
 */
public record ChangePasswordRequest(
        @Size(max = 64)
        String currentPassword,
        @NotBlank @Size(min = 8, max = 64)
        String newPassword
) {
}
