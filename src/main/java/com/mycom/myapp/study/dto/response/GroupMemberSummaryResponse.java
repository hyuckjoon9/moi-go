package com.mycom.myapp.study.dto.response;

import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import java.time.LocalDateTime;

public record GroupMemberSummaryResponse(Long userId, GroupRole role, LocalDateTime joinedAt) {

    public static GroupMemberSummaryResponse from(GroupMember groupMember) {
        return new GroupMemberSummaryResponse(
                groupMember.getUserId(), groupMember.getRole(), groupMember.getJoinedAt());
    }
}
