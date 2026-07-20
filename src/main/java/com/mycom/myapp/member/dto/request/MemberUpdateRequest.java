package com.mycom.myapp.member.dto.request;

import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(
        @Size(max = 50) String nickname,
        String bio,
        @Size(max = 255) String interests,
        @Size(max = 500) String profileImageUrl) {}
