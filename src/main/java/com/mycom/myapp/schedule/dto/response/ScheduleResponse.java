package com.mycom.myapp.schedule.dto.response;

import com.mycom.myapp.schedule.entity.StudySchedule;
import java.time.LocalDateTime;

public record ScheduleResponse(
        Long scheduleId,
        Long groupId,
        Long creatorId,
        String title,
        LocalDateTime scheduledAt,
        String location,
        String onlineLink,
        String content,
        String materials,
        LocalDateTime responseDeadline,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ScheduleResponse from(StudySchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getStudyGroup().getId(),
                schedule.getCreatorId(),
                schedule.getTitle(),
                schedule.getScheduledAt(),
                schedule.getLocation(),
                schedule.getOnlineLink(),
                schedule.getContent(),
                schedule.getMaterials(),
                schedule.getResponseDeadline(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt());
    }
}
