package com.mycom.myapp.attendance.dto.response;

import com.mycom.myapp.attendance.entity.AttendanceRecord;
import com.mycom.myapp.attendance.entity.AttendanceStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AttendanceSummaryResponse {

    private Long scheduleId;
    private int totalCount;
    private int presentCount;
    private int lateCount;
    private int absentCount;
    private int excusedCount;
    private List<MemberAttendance> members;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MemberAttendance {
        private Long userId;
        private AttendanceStatus status;
        private Long checkedBy;
        private LocalDateTime checkedAt;

        public static MemberAttendance from(AttendanceRecord record) {
            return MemberAttendance.builder()
                    .userId(record.getUserId())
                    .status(record.getStatus())
                    .checkedBy(record.getCheckedBy())
                    .checkedAt(record.getCheckedAt())
                    .build();
        }
    }

    public static AttendanceSummaryResponse of(Long scheduleId, List<AttendanceRecord> records) {
        List<MemberAttendance> members = records.stream().map(MemberAttendance::from).toList();

        return AttendanceSummaryResponse.builder()
                .scheduleId(scheduleId)
                .totalCount(records.size())
                .presentCount(countByStatus(records, AttendanceStatus.PRESENT))
                .lateCount(countByStatus(records, AttendanceStatus.LATE))
                .absentCount(countByStatus(records, AttendanceStatus.ABSENT))
                .excusedCount(countByStatus(records, AttendanceStatus.EXCUSED))
                .members(members)
                .build();
    }

    private static int countByStatus(List<AttendanceRecord> records, AttendanceStatus status) {
        return (int) records.stream().filter(r -> r.getStatus() == status).count();
    }
}
