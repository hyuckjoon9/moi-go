package com.mycom.myapp.study.service;

public record AddStudyGroupMemberCommand(Long postId, Long userId) {

    public AddStudyGroupMemberCommand {
        if (postId == null) {
            throw new IllegalArgumentException("모집글 식별자는 필수입니다.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("회원 식별자는 필수입니다.");
        }
    }
}
