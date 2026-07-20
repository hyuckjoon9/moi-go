package com.mycom.myapp.member.dto.response;

import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.member.entity.MemberStatus;
import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        String email,
        String nickname,
        String bio,
        String interests,
        String profileImageUrl,
        MemberRole role,
        MemberStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getBio(),
                member.getInterests(),
                member.getProfileImageUrl(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt(),
                member.getUpdatedAt());
    }
}
