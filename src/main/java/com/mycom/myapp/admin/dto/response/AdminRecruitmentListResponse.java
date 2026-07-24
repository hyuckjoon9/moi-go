package com.mycom.myapp.admin.dto.response;

import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import java.time.LocalDateTime;
import java.util.List;

public record AdminRecruitmentListResponse(
        List<Item> items, int page, int size, long totalElements, int totalPages) {

    public record Item(
            Long recruitmentId,
            Long leaderId,
            String leaderNickname,
            String title,
            String category,
            RecruitmentStatus status,
            RecruitmentVisibility visibility,
            LocalDateTime createdAt) {}
}
