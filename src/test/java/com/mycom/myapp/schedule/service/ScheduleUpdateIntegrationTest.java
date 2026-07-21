package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.dto.request.ScheduleUpdateRequest;
import com.mycom.myapp.schedule.dto.response.ScheduleResponse;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:schedule_update;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@Transactional
class ScheduleUpdateIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 20, 12, 0);

    @Autowired private ScheduleService service;
    @Autowired private StudyGroupRepository groupRepository;
    @Autowired private GroupMemberRepository memberRepository;
    @Autowired private StudyScheduleRepository scheduleRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean(name = "scheduleClock")
    private Clock clock;

    @BeforeEach
    void fixClock() {
        when(clock.instant()).thenReturn(NOW.atZone(ZONE).toInstant());
        when(clock.getZone()).thenReturn(ZONE);
    }

    @Test
    void updatesScheduleAndPreservesCreatorDeadlineAndCreatedAt() {
        StudyGroup group = saveGroupWithLeader(25L, 1L);
        LocalDateTime responseDeadline = NOW.minusHours(1);
        StudySchedule schedule =
                scheduleRepository.saveAndFlush(
                        StudySchedule.create(
                                group,
                                9L,
                                "기존 일정",
                                NOW.plusDays(2),
                                "기존 장소",
                                null,
                                "기존 내용",
                                "기존 준비물",
                                responseDeadline));
        Long scheduleId = schedule.getId();
        entityManager.clear();
        schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        LocalDateTime createdAt = schedule.getCreatedAt();

        ScheduleResponse response =
                service.update(
                        group.getId(),
                        1L,
                        scheduleId,
                        new ScheduleUpdateRequest(
                                "수정 일정", NOW.plusDays(3), null, "Discord", "수정 내용", null));
        entityManager.flush();
        entityManager.clear();
        StudySchedule persisted = scheduleRepository.findById(scheduleId).orElseThrow();

        assertThat(response.title()).isEqualTo("수정 일정");
        assertThat(persisted.getScheduledAt()).isEqualTo(NOW.plusDays(3));
        assertThat(persisted.getLocation()).isNull();
        assertThat(persisted.getOnlineLink()).isEqualTo("Discord");
        assertThat(persisted.getCreatorId()).isEqualTo(9L);
        assertThat(persisted.getResponseDeadline()).isEqualTo(responseDeadline);
        assertThat(persisted.getCreatedAt()).isEqualTo(createdAt);
        assertThat(persisted.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void doesNotChangeScheduleWhenNewTimeConflictsWithDeadline() {
        StudyGroup group = saveGroupWithLeader(25L, 1L);
        LocalDateTime originalScheduledAt = NOW.plusDays(3);
        StudySchedule schedule =
                scheduleRepository.saveAndFlush(
                        StudySchedule.create(
                                group,
                                1L,
                                "기존 일정",
                                originalScheduledAt,
                                null,
                                null,
                                null,
                                null,
                                NOW.plusDays(2)));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        group.getId(),
                                        1L,
                                        schedule.getId(),
                                        new ScheduleUpdateRequest(
                                                "수정 일정", NOW.plusDays(1), null, null, null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_SCHEDULE_TIME));
        assertThat(schedule.getTitle()).isEqualTo("기존 일정");
        assertThat(schedule.getScheduledAt()).isEqualTo(originalScheduledAt);
    }

    @Test
    void reportsNotFoundForScheduleOwnedByAnotherGroup() {
        StudyGroup allowedGroup = saveGroupWithLeader(25L, 1L);
        StudyGroup otherGroup = groupRepository.saveAndFlush(StudyGroup.create(26L, "다른 그룹"));
        StudySchedule otherSchedule =
                scheduleRepository.saveAndFlush(
                        StudySchedule.create(
                                otherGroup,
                                2L,
                                "다른 그룹 일정",
                                NOW.plusDays(2),
                                null,
                                null,
                                null,
                                null,
                                null));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        allowedGroup.getId(),
                                        1L,
                                        otherSchedule.getId(),
                                        new ScheduleUpdateRequest(
                                                "수정 일정", NOW.plusDays(3), null, null, null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND));
        assertThat(otherSchedule.getTitle()).isEqualTo("다른 그룹 일정");
    }

    private StudyGroup saveGroupWithLeader(Long postId, Long leaderId) {
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(postId, "알고리즘 스터디"));
        memberRepository.saveAndFlush(GroupMember.join(group, leaderId, GroupRole.LEADER));
        return group;
    }
}
