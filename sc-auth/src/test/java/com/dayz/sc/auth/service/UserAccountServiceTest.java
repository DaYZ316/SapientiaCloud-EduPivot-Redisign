package com.dayz.sc.auth.service;

import com.dayz.sc.auth.model.dto.OauthUserInfo;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.entity.UserIdentity;
import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Test
    void loginWithOauth_shouldCreateNewUserWithoutDefaultRole() {
        UserAccountService service = new UserAccountService(
                userAccountRepository,
                Clock.fixed(Instant.parse("2026-06-26T00:00:00Z"), ZoneOffset.UTC)
        );
        OauthUserInfo userInfo = new OauthUserInfo(
                OauthProvider.GOOGLE,
                "google-123",
                "oauth-user",
                "oauth@example.com",
                true,
                "OAuth User",
                "https://example.com/avatar.png",
                "zh-CN"
        );
        when(userAccountRepository.findIdentity(OauthProvider.GOOGLE, "google-123")).thenReturn(Optional.empty());
        when(userAccountRepository.findLinkedProviders(any())).thenReturn(List.of(OauthProvider.GOOGLE));

        UserAccountService.AuthenticatedUser authenticatedUser = service.loginWithOauth(userInfo, "127.0.0.1");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userAccountRepository).saveUser(userCaptor.capture());
        verify(userAccountRepository).saveIdentity(any(UserIdentity.class));
        assertThat(userCaptor.getValue().getRole()).isNull();
        assertThat(authenticatedUser.user().getRole()).isNull();
    }
}
