package com.mycom.myapp.attendance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycom.myapp.attendance.entity.AttendanceRecord;
import com.mycom.myapp.attendance.entity.AttendanceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AttendanceRecordRepositoryTest {

    @Autowired private AttendanceRecordRepository attendanceRecordRepository;

    @Test
    void findsRecordsByScheduleId() {
        AttendanceRecord first = save(10L, 20L, AttendanceStatus.PRESENT, 1L);
        AttendanceRecord second = save(10L, 21L, AttendanceStatus.ABSENT, 1L);
        save(11L, 20L, AttendanceStatus.PRESENT, 1L);

        assertThat(attendanceRecordRepository.findByScheduleId(10L))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void findsRecordByScheduleIdAndUserId() {
        AttendanceRecord record = save(10L, 20L, AttendanceStatus.PRESENT, 1L);

        assertThat(attendanceRecordRepository.findByScheduleIdAndUserId(10L, 20L)).contains(record);
        assertThat(attendanceRecordRepository.findByScheduleIdAndUserId(10L, 99L)).isEmpty();
    }

    @Test
    void reportsWhetherScheduleHasAttendanceRecords() {
        save(10L, 20L, AttendanceStatus.PRESENT, 1L);

        assertThat(attendanceRecordRepository.existsByScheduleId(10L)).isTrue();
        assertThat(attendanceRecordRepository.existsByScheduleId(99L)).isFalse();
    }

    @Test
    void countsByUserIdAndStatus() {
        save(10L, 20L, AttendanceStatus.PRESENT, 1L);
        save(11L, 20L, AttendanceStatus.PRESENT, 1L);
        save(12L, 20L, AttendanceStatus.ABSENT, 1L);

        assertThat(attendanceRecordRepository.countByUserIdAndStatus(20L, AttendanceStatus.PRESENT))
                .isEqualTo(2L);
        assertThat(attendanceRecordRepository.countByUserIdAndStatus(20L, AttendanceStatus.ABSENT))
                .isEqualTo(1L);
    }

    @Test
    void rejectsDuplicateScheduleAndUser() {
        save(10L, 20L, AttendanceStatus.PRESENT, 1L);

        assertThatThrownBy(
                        () ->
                                attendanceRecordRepository.saveAndFlush(
                                        AttendanceRecord.builder()
                                                .scheduleId(10L)
                                                .userId(20L)
                                                .status(AttendanceStatus.LATE)
                                                .checkedBy(1L)
                                                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private AttendanceRecord save(
            Long scheduleId, Long userId, AttendanceStatus status, Long checkedBy) {
        return attendanceRecordRepository.saveAndFlush(
                AttendanceRecord.builder()
                        .scheduleId(scheduleId)
                        .userId(userId)
                        .status(status)
                        .checkedBy(checkedBy)
                        .build());
    }
}
