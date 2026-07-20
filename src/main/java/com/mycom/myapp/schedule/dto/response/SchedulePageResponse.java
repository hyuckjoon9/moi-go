package com.mycom.myapp.schedule.dto.response;

import com.mycom.myapp.schedule.entity.StudySchedule;
import java.util.List;
import org.springframework.data.domain.Page;

public record SchedulePageResponse(
        List<ScheduleSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static SchedulePageResponse from(Page<StudySchedule> schedules) {
        List<ScheduleSummaryResponse> items =
                schedules.getContent().stream().map(ScheduleSummaryResponse::from).toList();
        return new SchedulePageResponse(
                items,
                schedules.getNumber(),
                schedules.getSize(),
                schedules.getTotalElements(),
                schedules.getTotalPages(),
                schedules.hasNext());
    }
}
