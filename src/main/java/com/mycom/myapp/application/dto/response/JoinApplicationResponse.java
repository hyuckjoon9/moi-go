package com.mycom.myapp.application.dto.response;

import java.time.LocalDateTime;

import com.mycom.myapp.application.entity.ApplicationStatus;
import com.mycom.myapp.application.entity.JoinApplication;

public record JoinApplicationResponse(
        Long id,
        Long postId,
        Long applicantId,
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
                    application.getMotivation(),
                    application.getExperience(),
                    application.getAvailableTime(),
                    application.getDesiredRole(),
                    application.getStatus(),
                    application.getAppliedAt());
        }}
