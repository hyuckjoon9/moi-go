package com.mycom.myapp.schedule.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.mycom.myapp.study.entity.StudyGroup;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StudyScheduleTest {

    private final StudyGroup group = StudyGroup.create(10L, "알고리즘 스터디");
    private final LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 25, 19, 0);

    @Test
    void createsScheduleWithoutLocationOrLink() {
        StudySchedule schedule =
                StudySchedule.create(group, 1L, "3주차", scheduledAt, null, null, null, null, null);

        assertThat(schedule.getStudyGroup()).isSameAs(group);
        assertThat(schedule.getCreatorId()).isEqualTo(1L);
        assertThat(schedule.getLocation()).isNull();
        assertThat(schedule.getOnlineLink()).isNull();
    }

    @Test
    void allowsDeadlineEqualToScheduledAt() {
        StudySchedule schedule =
                StudySchedule.create(
                        group, 1L, "3주차", scheduledAt, null, null, null, null, scheduledAt);

        assertThat(schedule.getResponseDeadline()).isEqualTo(scheduledAt);
    }

    @Test
    void rejectsDeadlineAfterScheduledAt() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                StudySchedule.create(
                                        group,
                                        1L,
                                        "3주차",
                                        scheduledAt,
                                        null,
                                        null,
                                        null,
                                        null,
                                        scheduledAt.plusMinutes(1)));
    }
}
