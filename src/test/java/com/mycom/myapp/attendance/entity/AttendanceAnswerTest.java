package com.mycom.myapp.attendance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttendanceAnswerTest {

    @Test
    void builderInitializesFieldsAndRespondedAt() {
        AttendanceAnswer answer =
                AttendanceAnswer.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .response(AttendanceResponse.UNDECIDED)
                        .build();

        assertThat(answer.getScheduleId()).isEqualTo(10L);
        assertThat(answer.getUserId()).isEqualTo(20L);
        assertThat(answer.getResponse()).isEqualTo(AttendanceResponse.UNDECIDED);
        assertThat(answer.getRespondedAt()).isNotNull();
    }

    @Test
    void changeResponseUpdatesResponseAndRespondedAt() throws InterruptedException {
        AttendanceAnswer answer =
                AttendanceAnswer.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .response(AttendanceResponse.UNDECIDED)
                        .build();
        var firstRespondedAt = answer.getRespondedAt();
        Thread.sleep(1);

        answer.changeResponse(AttendanceResponse.ATTEND);

        assertThat(answer.getResponse()).isEqualTo(AttendanceResponse.ATTEND);
        assertThat(answer.getRespondedAt()).isAfter(firstRespondedAt);
    }
}
