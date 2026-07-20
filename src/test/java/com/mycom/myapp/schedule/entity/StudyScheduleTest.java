package com.mycom.myapp.schedule.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.mycom.myapp.study.entity.StudyGroup;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void updatesEditableFieldsAndPreservesOwnedReferencesAndDeadline() {
        LocalDateTime responseDeadline = scheduledAt.minusHours(1);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        LocalDateTime previousUpdatedAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 20, 12, 0);
        StudySchedule schedule =
                StudySchedule.create(
                        group,
                        1L,
                        "기존 일정",
                        scheduledAt,
                        "기존 장소",
                        "기존 링크",
                        "기존 내용",
                        "기존 준비물",
                        responseDeadline);
        ReflectionTestUtils.setField(schedule, "createdAt", createdAt);
        ReflectionTestUtils.setField(schedule, "updatedAt", previousUpdatedAt);

        schedule.update(
                "수정 일정", scheduledAt.plusDays(1), null, "Discord", "수정 내용", null, modifiedAt);

        assertThat(schedule.getTitle()).isEqualTo("수정 일정");
        assertThat(schedule.getScheduledAt()).isEqualTo(scheduledAt.plusDays(1));
        assertThat(schedule.getLocation()).isNull();
        assertThat(schedule.getOnlineLink()).isEqualTo("Discord");
        assertThat(schedule.getContent()).isEqualTo("수정 내용");
        assertThat(schedule.getMaterials()).isNull();
        assertThat(schedule.getStudyGroup()).isSameAs(group);
        assertThat(schedule.getCreatorId()).isEqualTo(1L);
        assertThat(schedule.getResponseDeadline()).isEqualTo(responseDeadline);
        assertThat(schedule.getCreatedAt()).isEqualTo(createdAt);
        assertThat(schedule.getUpdatedAt()).isEqualTo(modifiedAt);
    }

    @Test
    void rejectsUpdateThatBreaksDeadlineInvariant() {
        StudySchedule schedule =
                StudySchedule.create(
                        group,
                        1L,
                        "기존 일정",
                        scheduledAt,
                        null,
                        null,
                        null,
                        null,
                        scheduledAt.minusHours(1));

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                schedule.update(
                                        "수정 일정",
                                        scheduledAt.minusHours(2),
                                        null,
                                        null,
                                        null,
                                        null,
                                        scheduledAt.minusDays(1)));
    }
}
