package com.mycom.myapp.study.dto.response;

import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.GroupStatus;
import com.mycom.myapp.study.entity.StudyGroup;
import java.time.LocalDateTime;
import java.util.List;

public record StudyGroupHomeResponse(
        Long groupId,
        Long postId,
        String name,
        GroupStatus status,
        LocalDateTime createdAt,
        GroupRole myRole,
        List<GroupMemberSummaryResponse> members) {

    public static StudyGroupHomeResponse of(
            StudyGroup studyGroup, GroupRole myRole, List<GroupMemberSummaryResponse> members) {
        return new StudyGroupHomeResponse(
                studyGroup.getId(),
                studyGroup.getPostId(),
                studyGroup.getName(),
                studyGroup.getStatus(),
                studyGroup.getCreatedAt(),
                myRole,
                members);
    }
}
