package com.mycom.myapp.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceAutoProcessingScheduler {

    private static final long FIXED_RATE_MILLIS = 60 * 60 * 1000;

    private final AttendanceService attendanceService;

    @Scheduled(fixedRate = FIXED_RATE_MILLIS)
    public void run() {
        attendanceService.autoProcessOverdueAttendance();
    }
}
