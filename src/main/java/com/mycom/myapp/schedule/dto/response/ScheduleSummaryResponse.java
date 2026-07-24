package com.mycom.myapp.schedule.dto.response;

import com.mycom.myapp.schedule.entity.StudySchedule;
import java.time.LocalDateTime;

public record ScheduleSummaryResponse(
        Long scheduleId,
        Long creatorId,
        String title,
        LocalDateTime scheduledAt,
        String location,
        String onlineLink,
        LocalDateTime responseDeadline) {

    public static ScheduleSummaryResponse from(StudySchedule schedule) {
        return new ScheduleSummaryResponse(
                schedule.getId(),
                schedule.getCreatorId(),
                schedule.getTitle(),
                schedule.getScheduledAt(),
                schedule.getLocation(),
                schedule.getOnlineLink(),
                schedule.getResponseDeadline());
    }
}
