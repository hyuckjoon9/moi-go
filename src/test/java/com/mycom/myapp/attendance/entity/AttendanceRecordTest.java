package com.mycom.myapp.attendance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttendanceRecordTest {

    @Test
    void builderInitializesFieldsAndCheckedAt() {
        AttendanceRecord record =
                AttendanceRecord.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .status(AttendanceStatus.PRESENT)
                        .checkedBy(1L)
                        .build();

        assertThat(record.getScheduleId()).isEqualTo(10L);
        assertThat(record.getUserId()).isEqualTo(20L);
        assertThat(record.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(record.getCheckedBy()).isEqualTo(1L);
        assertThat(record.getCheckedAt()).isNotNull();
    }

    @Test
    void updateStatusChangesStatusCheckedByAndCheckedAt() throws InterruptedException {
        AttendanceRecord record =
                AttendanceRecord.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .status(AttendanceStatus.ABSENT)
                        .checkedBy(1L)
                        .build();
        var firstCheckedAt = record.getCheckedAt();
        Thread.sleep(1);

        record.updateStatus(AttendanceStatus.LATE, 2L);

        assertThat(record.getStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(record.getCheckedBy()).isEqualTo(2L);
        assertThat(record.getCheckedAt()).isAfter(firstCheckedAt);
    }
}
