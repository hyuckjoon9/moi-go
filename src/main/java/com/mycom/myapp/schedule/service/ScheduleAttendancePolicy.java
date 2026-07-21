package com.mycom.myapp.schedule.service;

import com.mycom.myapp.study.entity.GroupStatus;
import java.time.LocalDateTime;

public record ScheduleAttendancePolicy(
        Long scheduleId,
        Long groupId,
        GroupStatus groupStatus,
        boolean activeGroupMember,
        LocalDateTime scheduledAt,
        LocalDateTime responseDeadline) {

    public LocalDateTime effectiveDeadline() {
        return responseDeadline != null ? responseDeadline : scheduledAt;
    }
}
