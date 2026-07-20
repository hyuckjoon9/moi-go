package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.dto.request.ScheduleCreateRequest;
import com.mycom.myapp.schedule.dto.response.ScheduleResponse;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class ScheduleServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 20, 12, 0);

    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    private final StudyGroupRepository groupRepository = mock(StudyGroupRepository.class);
    private final GroupMemberRepository memberRepository = mock(GroupMemberRepository.class);
    private final StudyScheduleRepository scheduleRepository = mock(StudyScheduleRepository.class);
    private final ScheduleService service =
            new ScheduleService(groupRepository, memberRepository, scheduleRepository, clock);

    @ParameterizedTest
    @EnumSource(
            value = GroupRole.class,
            names = {"LEADER", "MANAGER"})
    void createsScheduleForManagementRole(GroupRole role) {
        StudyGroup group = activeGroup();
        GroupMember member = GroupMember.join(group, 1L, role);
        allow(group, member);
        when(scheduleRepository.save(any(StudySchedule.class)))
                .thenAnswer(
                        invocation -> {
                            StudySchedule schedule = invocation.getArgument(0);
                            ReflectionTestUtils.setField(schedule, "id", 100L);
                            ReflectionTestUtils.setField(schedule, "createdAt", NOW);
                            ReflectionTestUtils.setField(schedule, "updatedAt", NOW);
                            return schedule;
                        });

        ScheduleResponse response = service.create(10L, 1L, validRequest());

        assertThat(response.scheduleId()).isEqualTo(100L);
        assertThat(response.groupId()).isEqualTo(10L);
        assertThat(response.creatorId()).isEqualTo(1L);
        assertThat(response.location()).isNull();
        assertThat(response.onlineLink()).isNull();
        verify(scheduleRepository).save(any(StudySchedule.class));
    }

    @Test
    void rejectsMissingGroup() {
        when(groupRepository.findById(10L)).thenReturn(Optional.empty());

        assertError(ErrorCode.GROUP_NOT_FOUND, validRequest());
    }

    @Test
    void rejectsMissingMembership() {
        StudyGroup group = activeGroup();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(memberRepository.findByStudyGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertError(ErrorCode.GROUP_ACCESS_DENIED, validRequest());
    }

    @Test
    void rejectsWithdrawnMember() {
        StudyGroup group = activeGroup();
        GroupMember member = GroupMember.join(group, 1L, GroupRole.LEADER);
        member.withdraw();
        allow(group, member);

        assertError(ErrorCode.WITHDRAWN_GROUP_MEMBER, validRequest());
    }

    @Test
    void rejectsEndedGroupBeforeRoleCheck() {
        StudyGroup group = activeGroup();
        group.end();
        allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));

        assertError(ErrorCode.GROUP_ENDED, validRequest());
    }

    @Test
    void rejectsActiveMemberWithoutManagementRole() {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));

        assertError(ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN, validRequest());
    }

    @ParameterizedTest
    @MethodSource("invalidTimeRequests")
    void rejectsInvalidScheduleTimes(ScheduleCreateRequest request) {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));

        assertError(ErrorCode.INVALID_SCHEDULE_TIME, request);
    }

    @Test
    void allowsDeadlineEqualToScheduledAt() {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
        when(scheduleRepository.save(any(StudySchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime scheduledAt = NOW.plusHours(2);

        service.create(10L, 1L, request(scheduledAt, scheduledAt));

        verify(scheduleRepository).save(any(StudySchedule.class));
    }

    private static Stream<ScheduleCreateRequest> invalidTimeRequests() {
        return Stream.of(
                request(NOW, null),
                request(NOW.minusSeconds(1), null),
                request(NOW.plusHours(2), NOW),
                request(NOW.plusHours(2), NOW.plusHours(3)));
    }

    private void assertError(ErrorCode errorCode, ScheduleCreateRequest request) {
        assertThatThrownBy(() -> service.create(10L, 1L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
        verify(scheduleRepository, never()).save(any());
    }

    private void allow(StudyGroup group, GroupMember member) {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(memberRepository.findByStudyGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));
    }

    private StudyGroup activeGroup() {
        StudyGroup group = StudyGroup.create(25L, "알고리즘 스터디");
        ReflectionTestUtils.setField(group, "id", 10L);
        return group;
    }

    private ScheduleCreateRequest validRequest() {
        return request(NOW.plusHours(2), NOW.plusHours(1));
    }

    private static ScheduleCreateRequest request(
            LocalDateTime scheduledAt, LocalDateTime responseDeadline) {
        return new ScheduleCreateRequest(
                "3주차 스터디", scheduledAt, null, null, "3장 문제 풀이", "교재와 노트북", responseDeadline);
    }
}
