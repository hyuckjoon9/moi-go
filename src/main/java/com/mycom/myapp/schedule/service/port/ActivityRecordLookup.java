package com.mycom.myapp.schedule.service.port;

public interface ActivityRecordLookup {
    boolean existsByScheduleId(Long scheduleId);
}
