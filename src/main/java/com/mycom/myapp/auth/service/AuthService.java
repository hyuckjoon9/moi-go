package com.mycom.myapp.auth.service;

import com.mycom.myapp.auth.dto.request.LoginRequest;
import com.mycom.myapp.auth.dto.request.ReissueRequest;
import com.mycom.myapp.auth.dto.response.TokenResponse;
import com.mycom.myapp.auth.entity.RefreshToken;
import com.mycom.myapp.auth.repository.RefreshTokenRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.security.jwt.JwtTokenProvider;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.entity.MemberStatus;
import com.mycom.myapp.member.repository.MemberRepository;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Member member =
                memberRepository
                        .findByEmail(request.email())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (member.getStatus() != MemberStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokens(member);
    }

    @Transactional
    public TokenResponse reissue(ReissueRequest request) {
        jwtTokenProvider.parseClaims(request.refreshToken());
        RefreshToken savedToken =
                refreshTokenRepository
                        .findByToken(request.refreshToken())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (savedToken.isExpired(LocalDateTime.now())) {
            refreshTokenRepository.delete(savedToken);
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }
        Member member =
                memberRepository
                        .findById(savedToken.getUserId())
                        .filter(found -> found.getStatus() == MemberStatus.ACTIVE)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        refreshTokenRepository.delete(savedToken);
        return issueTokens(member);
    }

    @Transactional
    public void logout(ReissueRequest request) {
        refreshTokenRepository.deleteByToken(request.refreshToken());
    }

    private TokenResponse issueTokens(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = jwtTokenProvider.createRefreshToken(member);
        refreshTokenRepository.deleteByUserId(member.getId());
        refreshTokenRepository.save(
                RefreshToken.create(
                        member.getId(), refreshToken, jwtTokenProvider.getRefreshTokenExpiresAt()));
        return TokenResponse.bearer(accessToken, refreshToken);
    }
}
