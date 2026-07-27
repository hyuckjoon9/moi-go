package com.mycom.myapp.attendance.dto.response;

import com.mycom.myapp.attendance.entity.AttendanceRecord;
import com.mycom.myapp.attendance.entity.AttendanceStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AttendanceRecordResponse {

    private Long id;
    private Long scheduleId;
    private Long userId;
    private AttendanceStatus status;
    private Long checkedBy;
    private LocalDateTime checkedAt;

    public static AttendanceRecordResponse of(AttendanceRecord record) {
        return AttendanceRecordResponse.builder()
                .id(record.getId())
                .scheduleId(record.getScheduleId())
                .userId(record.getUserId())
                .status(record.getStatus())
                .checkedBy(record.getCheckedBy())
                .checkedAt(record.getCheckedAt())
                .build();
    }
}
