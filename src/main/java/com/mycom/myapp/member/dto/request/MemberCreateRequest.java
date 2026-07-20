package com.mycom.myapp.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberCreateRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 50) String nickname,
        String bio,
        @Size(max = 255) String interests,
        @Size(max = 500) String profileImageUrl) {}
