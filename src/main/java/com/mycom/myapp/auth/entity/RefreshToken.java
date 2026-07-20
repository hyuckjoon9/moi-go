package com.mycom.myapp.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_refresh_tokens_token", columnNames = "token"))
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected RefreshToken() {}

    private RefreshToken(Long userId, String token, LocalDateTime expiresAt) {
        if (userId == null) {
            throw new IllegalArgumentException("회원 식별자는 필수입니다.");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Refresh Token은 필수입니다.");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("만료 시각은 필수입니다.");
        }
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken create(Long userId, String token, LocalDateTime expiresAt) {
        return new RefreshToken(userId, token, expiresAt);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
