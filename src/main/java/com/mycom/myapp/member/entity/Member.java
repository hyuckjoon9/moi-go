package com.mycom.myapp.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
            @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname")
        })
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(length = 255)
    private String interests;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private MemberStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Member() {}

    private Member(
            String email,
            String password,
            String nickname,
            String bio,
            String interests,
            String profileImageUrl) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임은 필수입니다.");
        }
        this.email = email.strip();
        this.password = password;
        this.nickname = nickname.strip();
        this.bio = normalizeNullable(bio);
        this.interests = normalizeNullable(interests);
        this.profileImageUrl = normalizeNullable(profileImageUrl);
        this.role = MemberRole.USER;
        this.status = MemberStatus.ACTIVE;
    }

    public static Member create(
            String email,
            String encodedPassword,
            String nickname,
            String bio,
            String interests,
            String profileImageUrl) {
        return new Member(email, encodedPassword, nickname, bio, interests, profileImageUrl);
    }

    public void updateProfile(
            String nickname, String bio, String interests, String profileImageUrl) {
        if (nickname != null) {
            if (nickname.isBlank()) {
                throw new IllegalArgumentException("닉네임은 공백일 수 없습니다.");
            }
            this.nickname = nickname.strip();
        }
        if (bio != null) {
            this.bio = normalizeNullable(bio);
        }
        if (interests != null) {
            this.interests = normalizeNullable(interests);
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = normalizeNullable(profileImageUrl);
        }
    }

    public void withdraw() {
        status = MemberStatus.WITHDRAWN;
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    @PrePersist
    private void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public String getBio() {
        return bio;
    }

    public String getInterests() {
        return interests;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public MemberRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
