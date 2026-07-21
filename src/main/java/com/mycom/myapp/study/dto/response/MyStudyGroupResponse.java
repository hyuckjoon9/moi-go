package com.mycom.myapp.study.dto.response;

import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.GroupStatus;
import com.mycom.myapp.study.entity.StudyGroup;
import java.time.LocalDateTime;

public record MyStudyGroupResponse(
        Long groupId,
        Long postId,
        String name,
        GroupStatus status,
        GroupRole role,
        LocalDateTime joinedAt) {

    public static MyStudyGroupResponse from(GroupMember member) {
        StudyGroup group = member.getStudyGroup();
        return new MyStudyGroupResponse(
                group.getId(),
                group.getPostId(),
                group.getName(),
                group.getStatus(),
                member.getRole(),
                member.getJoinedAt());
    }
}
