package com.mycom.myapp.admin.dto.response;

import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.member.entity.MemberStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AdminMemberListResponse(
        List<Item> items, int page, int size, long totalElements, int totalPages) {

    public record Item(
            Long memberId,
            String email,
            String nickname,
            MemberRole role,
            MemberStatus status,
            LocalDateTime createdAt) {}
}
