package com.mycom.myapp.application.dto.response;

import com.mycom.myapp.application.entity.ApplicationStatus;
import com.mycom.myapp.application.entity.JoinApplication;
import java.time.LocalDateTime;

public record JoinApplicationResponse(
        Long id,
        Long postId,
        Long applicantId,
        String applicantNickname,
        String motivation,
        String experience,
        String availableTime,
        String desiredRole,
        ApplicationStatus status,
        LocalDateTime appliedAt) {

    public static JoinApplicationResponse from(JoinApplication application) {
        return new JoinApplicationResponse(
                application.getId(),
                application.getPost().getId(),
                application.getApplicant().getId(),
                application.getApplicant().getNickname(),
                application.getMotivation(),
                application.getExperience(),
                application.getAvailableTime(),
                application.getDesiredRole(),
                application.getStatus(),
                application.getAppliedAt());
    }
}
