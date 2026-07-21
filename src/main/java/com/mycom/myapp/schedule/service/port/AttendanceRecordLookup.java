package com.mycom.myapp.schedule.service.port;

public interface AttendanceRecordLookup {
    boolean existsByScheduleId(Long scheduleId);
}
