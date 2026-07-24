package com.mycom.myapp.attendance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycom.myapp.attendance.entity.AttendanceAnswer;
import com.mycom.myapp.attendance.entity.AttendanceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AttendanceResponseRepositoryTest {

    @Autowired private AttendanceResponseRepository attendanceResponseRepository;

    @Test
    void findsAnswersByScheduleId() {
        AttendanceAnswer first = save(10L, 20L, AttendanceResponse.ATTEND);
        AttendanceAnswer second = save(10L, 21L, AttendanceResponse.UNDECIDED);
        save(11L, 20L, AttendanceResponse.ABSENT);

        assertThat(attendanceResponseRepository.findByScheduleId(10L))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void findsAnswerByScheduleIdAndUserId() {
        AttendanceAnswer answer = save(10L, 20L, AttendanceResponse.ATTEND);

        assertThat(attendanceResponseRepository.findByScheduleIdAndUserId(10L, 20L))
                .contains(answer);
        assertThat(attendanceResponseRepository.findByScheduleIdAndUserId(10L, 99L)).isEmpty();
    }

    @Test
    void rejectsDuplicateScheduleAndUser() {
        save(10L, 20L, AttendanceResponse.ATTEND);

        assertThatThrownBy(
                        () ->
                                attendanceResponseRepository.saveAndFlush(
                                        AttendanceAnswer.builder()
                                                .scheduleId(10L)
                                                .userId(20L)
                                                .response(AttendanceResponse.UNDECIDED)
                                                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private AttendanceAnswer save(Long scheduleId, Long userId, AttendanceResponse response) {
        return attendanceResponseRepository.saveAndFlush(
                AttendanceAnswer.builder()
                        .scheduleId(scheduleId)
                        .userId(userId)
                        .response(response)
                        .build());
    }
}
