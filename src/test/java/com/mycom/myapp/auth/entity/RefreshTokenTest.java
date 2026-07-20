package com.mycom.myapp.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    // RefreshToken 생성 성공 테스트:
    // 회원 ID, token 값, 만료 시각을 전달하면 엔티티가 해당 값을 그대로 보관해야 한다.
    // 로그인 또는 토큰 재발급 시 DB에 저장되는 refresh token의 기본 생성 규칙을 검증한다.
    @Test
    @DisplayName("RefreshToken 생성 시 회원 ID, token, 만료 시각을 저장한다")
    void createStoresTokenInformation() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);

        RefreshToken refreshToken = RefreshToken.create(1L, "refresh-token", expiresAt);

        assertThat(refreshToken.getUserId()).isEqualTo(1L);
        assertThat(refreshToken.getToken()).isEqualTo("refresh-token");
        assertThat(refreshToken.getExpiresAt()).isEqualTo(expiresAt);
    }

    // RefreshToken 만료 판단 테스트:
    // expiresAt이 현재 검증 시각보다 이전이거나 같으면 만료된 token으로 판단한다.
    // 만료된 refresh token은 재발급에 사용할 수 없어야 한다.
    @Test
    @DisplayName("만료 시각이 지난 refresh token을 만료 상태로 판단한다")
    void detectsExpiredToken() {
        RefreshToken refreshToken = RefreshToken.create(1L, "refresh-token", LocalDateTime.now());

        assertThat(refreshToken.isExpired(LocalDateTime.now().plusSeconds(1))).isTrue();
    }

    // RefreshToken 필수값 검증 테스트:
    // 회원 ID, token 값, 만료 시각은 refresh token 저장에 반드시 필요한 값이다.
    // null 또는 공백 token이 들어오면 잘못된 엔티티가 생성되지 않도록 예외를 발생시킨다.
    @Test
    @DisplayName("RefreshToken 생성 시 필수값이 없으면 예외를 발생시킨다")
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
