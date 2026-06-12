package com.dayz.sc.common.security.support;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class JwtPrincipalResolver {

    private JwtPrincipalResolver() {
    }

    public static UUID requireUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED);
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "Invalid user id in token");
        }
    }

    public static Integer role(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        Object role = jwt.getClaims().get("role");
        if (role instanceof Number number) {
            return number.intValue();
        }
        if (role instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
