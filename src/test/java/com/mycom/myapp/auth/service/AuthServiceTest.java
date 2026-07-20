package com.mycom.myapp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.auth.dto.request.LoginRequest;
import com.mycom.myapp.auth.dto.request.ReissueRequest;
import com.mycom.myapp.auth.dto.response.TokenResponse;
import com.mycom.myapp.auth.entity.RefreshToken;
import com.mycom.myapp.auth.repository.RefreshTokenRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.security.jwt.JwtTokenProvider;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final RefreshTokenRepository refreshTokenRepository =
            mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final AuthService authService =
            new AuthService(
                    memberRepository, refreshTokenRepository, passwordEncoder, jwtTokenProvider);

    // 로그인 성공 테스트:
    // 이메일로 회원을 조회하고 비밀번호가 일치하면 access token과 refresh token을 발급한다.
    // 기존 refresh token은 사용자 기준으로 삭제한 뒤 새 refresh token을 저장해야 한다.
    @Test
    @DisplayName("로그인 성공 시 토큰을 발급하고 새 refresh token을 저장한다")
    void logsInAndStoresNewRefreshToken() {
        Member member = activeMember(1L);
        when(memberRepository.findByEmail("user@moigo.test")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(member)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(member)).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiresAt())
                .thenReturn(LocalDateTime.now().plusDays(14));

        TokenResponse response =
                authService.login(new LoginRequest("user@moigo.test", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(refreshTokenRepository).deleteByUserId(1L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // 로그인 실패 테스트:
    // 이메일로 회원을 찾았더라도 입력 비밀번호가 저장된 암호화 비밀번호와 일치하지 않으면
    // 인증 실패 예외(INVALID_CREDENTIALS)를 반환해야 한다.
    @Test
    @DisplayName("비밀번호가 일치하지 않으면 로그인을 거부한다")
    void rejectsLoginWhenPasswordDoesNotMatch() {
        Member member = activeMember(1L);
        when(memberRepository.findByEmail("user@moigo.test")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertError(
                ErrorCode.INVALID_CREDENTIALS,
                () -> authService.login(new LoginRequest("user@moigo.test", "wrong-password")));
    }

    // 토큰 재발급 성공 테스트:
    // 전달받은 refresh token의 서명과 DB 저장 여부를 확인한 뒤 기존 token을 삭제한다.
    // 이후 같은 회원에게 새 access token과 refresh token을 발급하고 새 refresh token을 저장한다.
    @Test
    @DisplayName("유효한 refresh token이면 토큰을 재발급하고 사용된 refresh token을 삭제한다")
    void reissuesTokensAndDeletesUsedRefreshToken() {
        Member member = activeMember(1L);
        RefreshToken savedToken =
                RefreshToken.create(1L, "old-refresh-token", LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByToken("old-refresh-token"))
                .thenReturn(Optional.of(savedToken));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(jwtTokenProvider.createAccessToken(member)).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken(member)).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiresAt())
                .thenReturn(LocalDateTime.now().plusDays(14));

        TokenResponse response = authService.reissue(new ReissueRequest("old-refresh-token"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        verify(jwtTokenProvider).parseClaims("old-refresh-token");
        verify(refreshTokenRepository).delete(savedToken);
        verify(refreshTokenRepository).deleteByUserId(1L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // 토큰 재발급 실패 테스트:
    // JWT 형식 검증을 통과하더라도 DB에 저장된 refresh token이 아니면 재발급하면 안 된다.
    // 로그아웃되었거나 이미 재발급에 사용된 token을 막는 케이스다.
    @Test
    @DisplayName("DB에 저장되지 않은 refresh token이면 재발급을 거부한다")
    void rejectsReissueWhenRefreshTokenIsNotSaved() {
        when(refreshTokenRepository.findByToken("missing-refresh-token"))
                .thenReturn(Optional.empty());

        assertError(
                ErrorCode.INVALID_TOKEN,
                () -> authService.reissue(new ReissueRequest("missing-refresh-token")));
        verify(jwtTokenProvider).parseClaims("missing-refresh-token");
    }

    // 만료 refresh token 테스트:
    // DB에 token이 남아 있더라도 만료 시각이 지났다면 재발급하지 않는다.
    // 만료된 token은 재사용되지 않도록 삭제한다.
    @Test
    @DisplayName("만료된 refresh token이면 삭제하고 재발급을 거부한다")
    void rejectsExpiredRefreshTokenAndDeletesIt() {
        RefreshToken expiredToken =
                RefreshToken.create(
                        1L, "expired-refresh-token", LocalDateTime.now().minusSeconds(1));
        when(refreshTokenRepository.findByToken("expired-refresh-token"))
                .thenReturn(Optional.of(expiredToken));

        assertError(
                ErrorCode.EXPIRED_TOKEN,
                () -> authService.reissue(new ReissueRequest("expired-refresh-token")));
        verify(refreshTokenRepository).delete(expiredToken);
    }

    // 로그아웃 테스트:
    // 로그아웃 요청으로 전달받은 refresh token을 DB에서 삭제한다.
    // 이후 같은 refresh token으로 재발급을 시도하면 실패해야 한다.
    @Test
    @DisplayName("로그아웃 시 전달받은 refresh token을 삭제한다")
    void logoutDeletesRefreshTokenByValue() {
        authService.logout(new ReissueRequest("refresh-token"));

        verify(refreshTokenRepository).deleteByToken("refresh-token");
    }

    private Member activeMember(Long id) {
        Member member =
                Member.create("user@moigo.test", "encoded-password", "모이고", null, null, null);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private void assertError(
            ErrorCode errorCode, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
