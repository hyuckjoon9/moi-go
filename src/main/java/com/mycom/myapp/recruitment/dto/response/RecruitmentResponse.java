package com.mycom.myapp.recruitment.dto.response;

import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RecruitmentResponse(
        Long id,
        Long leaderId,
        String title,
        String category,
        String description,
        String goal,
        String method,
        String meetingType,
        String location,
        String onlineLink,
        String meetingDay,
        int capacity,
        LocalDate recruitmentDeadline,
        String expectedDuration,
        String conditions,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static RecruitmentResponse from(RecruitmentPost post) {
        return new RecruitmentResponse(
                post.getId(),
                post.getLeader().getId(),
                post.getTitle(),
                post.getCategory(),
                post.getDescription(),
                post.getGoal(),
                post.getMethod(),
                post.getMeetingType(),
                post.getLocation(),
                post.getOnlineLink(),
                post.getMeetingDay(),
                post.getCapacity(),
                post.getRecruitmentDeadline(),
                post.getExpectedDuration(),
                post.getConditions(),
                post.getStatus().name(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
