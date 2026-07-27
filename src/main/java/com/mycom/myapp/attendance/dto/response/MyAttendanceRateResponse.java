package com.mycom.myapp.attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MyAttendanceRateResponse {

    private Long userId;
    private long totalCount;
    private long presentCount;
    private long lateCount;
    private long absentCount;
    private long excusedCount;
    private double attendanceRate;

    public static MyAttendanceRateResponse of(
            Long userId, long presentCount, long lateCount, long absentCount, long excusedCount) {
        long totalCount = presentCount + lateCount + absentCount + excusedCount;
        double attendanceRate = totalCount == 0 ? 0.0 : (double) presentCount / totalCount * 100;
        return MyAttendanceRateResponse.builder()
                .userId(userId)
                .totalCount(totalCount)
                .presentCount(presentCount)
                .lateCount(lateCount)
                .absentCount(absentCount)
                .excusedCount(excusedCount)
                .attendanceRate(attendanceRate)
                .build();
    }
}
