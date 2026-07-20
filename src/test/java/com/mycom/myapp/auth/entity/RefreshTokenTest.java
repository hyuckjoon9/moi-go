package com.mycom.myapp.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    void createStoresTokenInformation() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);

        RefreshToken refreshToken = RefreshToken.create(1L, "refresh-token", expiresAt);

        assertThat(refreshToken.getUserId()).isEqualTo(1L);
        assertThat(refreshToken.getToken()).isEqualTo("refresh-token");
        assertThat(refreshToken.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void detectsExpiredToken() {
        RefreshToken refreshToken = RefreshToken.create(1L, "refresh-token", LocalDateTime.now());

        assertThat(refreshToken.isExpired(LocalDateTime.now().plusSeconds(1))).isTrue();
    }

    @Test
    void createRejectsRequiredFields() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> RefreshToken.create(null, "refresh-token", expiresAt));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RefreshToken.create(1L, " ", expiresAt));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RefreshToken.create(1L, "refresh-token", null));
    }
}
