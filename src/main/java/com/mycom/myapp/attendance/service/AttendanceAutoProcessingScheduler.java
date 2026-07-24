package com.mycom.myapp.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 조회 시점 지연 처리(getSummary/getMyAttendanceRate/getGroupAttendanceRates)를 아무도 트리거하지 않는 경우를 대비한 안전망.
 * 1시간마다 전체 스케줄을 훑어 자동 결석 처리 대상을 채운다.
 */
@Component
@RequiredArgsConstructor
public class AttendanceAutoProcessingScheduler {

    private static final long FIXED_RATE_MILLIS = 60 * 60 * 1000; // 1시간마다

    private final AttendanceService attendanceService;

    @Scheduled(fixedRate = FIXED_RATE_MILLIS)
    public void run() {
        attendanceService.autoProcessOverdueAttendance();
    }
}
