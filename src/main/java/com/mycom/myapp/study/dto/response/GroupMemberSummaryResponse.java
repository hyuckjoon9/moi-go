package com.mycom.myapp.study.dto.response;

import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import java.time.LocalDateTime;

public record GroupMemberSummaryResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        GroupRole role,
        LocalDateTime joinedAt) {

    public static GroupMemberSummaryResponse of(
            GroupMember groupMember, String nickname, String profileImageUrl) {
        return new GroupMemberSummaryResponse(
                groupMember.getUserId(),
                nickname,
                profileImageUrl,
                groupMember.getRole(),
                groupMember.getJoinedAt());
    }
}
