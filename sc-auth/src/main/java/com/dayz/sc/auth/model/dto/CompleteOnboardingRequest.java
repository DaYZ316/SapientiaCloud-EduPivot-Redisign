package com.dayz.sc.auth.model.dto;

import jakarta.validation.constraints.*;

/**
 * Request used to complete the first-login onboarding profile.
 *
 * @author DaYZ
 * @since 2026-06-26
 */
public record CompleteOnboardingRequest(
        @NotNull
        @Min(1)
        @Max(2)
        Integer role,
        @NotBlank
        @Size(max = 128)
        String displayName
) {
}
