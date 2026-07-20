package com.mycom.myapp.study.service;

import java.util.List;

public record CreateStudyGroupCommand(
        Long postId, String groupName, Long leaderUserId, List<Long> approvedUserIds) {

    public CreateStudyGroupCommand {
        if (postId == null) {
            throw new IllegalArgumentException("모집글 식별자는 필수입니다.");
        }
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("그룹 이름은 필수입니다.");
        }
        if (leaderUserId == null) {
            throw new IllegalArgumentException("모집장 식별자는 필수입니다.");
        }
        if (approvedUserIds == null) {
            throw new IllegalArgumentException("승인 회원 목록은 필수입니다.");
        }
        if (approvedUserIds.stream().anyMatch(userId -> userId == null)) {
            throw new IllegalArgumentException("승인 회원 식별자는 null일 수 없습니다.");
        }

        groupName = groupName.strip();
        approvedUserIds = List.copyOf(approvedUserIds);
    }
}
