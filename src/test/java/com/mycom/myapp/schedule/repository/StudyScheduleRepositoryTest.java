package com.mycom.myapp.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class StudyScheduleRepositoryTest {

    @Autowired private StudyGroupRepository groupRepository;
    @Autowired private StudyScheduleRepository scheduleRepository;

    @Test
    void storesLocationUndecidedScheduleAndInitializesTimestamps() {
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));
        StudySchedule schedule =
                StudySchedule.create(
                        group,
                        1L,
                        "장소 미정 일정",
                        LocalDateTime.of(2026, 7, 25, 19, 0),
                        null,
                        null,
                        null,
                        null,
                        null);

        StudySchedule saved = scheduleRepository.saveAndFlush(schedule);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLocation()).isNull();
        assertThat(saved.getOnlineLink()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void findsGroupSchedulesByScheduledAtAscending() {
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));
        scheduleRepository.save(schedule(group, "세 번째 일정", LocalDateTime.of(2026, 7, 27, 19, 0)));
        scheduleRepository.save(schedule(group, "첫 일정", LocalDateTime.of(2026, 7, 25, 19, 0)));
        scheduleRepository.saveAndFlush(
                schedule(group, "두 번째 일정", LocalDateTime.of(2026, 7, 26, 19, 0)));

        assertThat(scheduleRepository.findAllByStudyGroupIdOrderByScheduledAtAsc(group.getId()))
                .extracting(StudySchedule::getTitle)
                .containsExactly("첫 일정", "두 번째 일정", "세 번째 일정");
    }

    private StudySchedule schedule(StudyGroup group, String title, LocalDateTime scheduledAt) {
        return StudySchedule.create(group, 1L, title, scheduledAt, null, null, null, null, null);
    }
}
