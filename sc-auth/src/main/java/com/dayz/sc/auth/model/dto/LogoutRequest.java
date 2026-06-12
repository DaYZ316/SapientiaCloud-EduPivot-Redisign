package com.dayz.sc.auth.model.dto;

import org.jspecify.annotations.Nullable;

public record LogoutRequest(
        @Nullable String refreshToken
) {
}
