package com.mycom.myapp.admin.dto.response;

import com.mycom.myapp.admin.entity.AdminAction;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminRecruitmentDetailResponse(
        Long recruitmentId,
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
        RecruitmentStatus status,
        RecruitmentVisibility visibility,
        Long groupId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<RecentAction> recentActions) {

    public record RecentAction(AdminAction action, String reason, LocalDateTime createdAt) {}
}
