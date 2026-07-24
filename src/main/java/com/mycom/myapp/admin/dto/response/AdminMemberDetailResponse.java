package com.mycom.myapp.admin.dto.response;

import com.mycom.myapp.admin.entity.AdminAction;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.member.entity.MemberStatus;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.GroupStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AdminMemberDetailResponse(
        Long memberId,
        String email,
        String nickname,
        String bio,
        String interests,
        String profileImageUrl,
        MemberRole role,
        MemberStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<GroupSummary> groups,
        List<RecentAction> recentActions) {

    public record GroupSummary(Long groupId, String name, GroupRole role, GroupStatus status) {}

    public record RecentAction(AdminAction action, String reason, LocalDateTime createdAt) {}
}
