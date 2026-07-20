package com.mycom.myapp.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void findsUpcomingSchedulesFromBoundaryWithStableAscendingOrder() {
        LocalDateTime boundary = LocalDateTime.of(2026, 7, 20, 12, 0);
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(20L, "알고리즘 스터디"));
        scheduleRepository.save(schedule(group, "지난 일정", boundary.minusSeconds(1)));
        StudySchedule sameTimeFirst =
                scheduleRepository.save(schedule(group, "동시 일정 1", boundary.plusHours(1)));
        StudySchedule sameTimeSecond =
                scheduleRepository.save(schedule(group, "동시 일정 2", boundary.plusHours(1)));
        scheduleRepository.saveAndFlush(schedule(group, "경계 일정", boundary));

        Page<StudySchedule> result =
                scheduleRepository
                        .findAllByStudyGroupIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(
                                group.getId(), boundary, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(StudySchedule::getTitle)
                .containsExactly("경계 일정", "동시 일정 1", "동시 일정 2");
        assertThat(sameTimeFirst.getId()).isLessThan(sameTimeSecond.getId());
    }

    @Test
    void findsPastSchedulesBeforeBoundaryWithStableDescendingOrder() {
        LocalDateTime boundary = LocalDateTime.of(2026, 7, 20, 12, 0);
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(21L, "백엔드 스터디"));
        scheduleRepository.save(schedule(group, "오래된 일정", boundary.minusDays(2)));
        scheduleRepository.save(schedule(group, "최근 동시 일정 1", boundary.minusHours(1)));
        scheduleRepository.save(schedule(group, "최근 동시 일정 2", boundary.minusHours(1)));
        scheduleRepository.saveAndFlush(schedule(group, "경계 일정", boundary));

        Page<StudySchedule> result =
                scheduleRepository
                        .findAllByStudyGroupIdAndScheduledAtLessThanOrderByScheduledAtDescIdDesc(
                                group.getId(), boundary, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(StudySchedule::getTitle)
                .containsExactly("최근 동시 일정 2", "최근 동시 일정 1", "오래된 일정");
    }

    @Test
    void findsScheduleOnlyInsideRequestedGroup() {
        StudyGroup requestedGroup = groupRepository.saveAndFlush(StudyGroup.create(22L, "요청 그룹"));
        StudyGroup otherGroup = groupRepository.saveAndFlush(StudyGroup.create(23L, "다른 그룹"));
        StudySchedule requestedSchedule =
                scheduleRepository.saveAndFlush(
                        schedule(requestedGroup, "요청 일정", LocalDateTime.of(2026, 7, 25, 19, 0)));

        assertThat(
                        scheduleRepository.findByIdAndStudyGroupId(
                                requestedSchedule.getId(), requestedGroup.getId()))
                .contains(requestedSchedule);
        assertThat(
                        scheduleRepository.findByIdAndStudyGroupId(
                                requestedSchedule.getId(), otherGroup.getId()))
                .isEmpty();
    }

    private StudySchedule schedule(StudyGroup group, String title, LocalDateTime scheduledAt) {
        return StudySchedule.create(group, 1L, title, scheduledAt, null, null, null, null, null);
    }
}
