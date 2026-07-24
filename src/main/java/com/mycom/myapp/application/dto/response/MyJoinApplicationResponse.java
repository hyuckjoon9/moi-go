package com.mycom.myapp.application.dto.response;

import com.mycom.myapp.application.entity.ApplicationStatus;
import com.mycom.myapp.application.entity.JoinApplication;
import java.time.LocalDateTime;

public record MyJoinApplicationResponse(
        Long applicationId,
        Long postId,
        String postTitle,
        String category,
        ApplicationStatus status,
        LocalDateTime appliedAt) {

    public static MyJoinApplicationResponse from(JoinApplication application) {
        return new MyJoinApplicationResponse(
                application.getId(),
                application.getPost().getId(),
                application.getPost().getTitle(),
                application.getPost().getCategory(),
                application.getStatus(),
                application.getAppliedAt());
    }
}
