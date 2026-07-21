package com.mycom.myapp.schedule.service.port;

import com.mycom.myapp.schedule.service.ScheduleAttendancePolicy;

public interface ScheduleAttendancePolicyReader {
    ScheduleAttendancePolicy getAttendancePolicy(Long scheduleId, Long userId);
}
