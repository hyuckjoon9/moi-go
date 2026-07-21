package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.dto.request.ScheduleCreateRequest;
import com.mycom.myapp.schedule.dto.request.ScheduleScope;
import com.mycom.myapp.schedule.dto.request.ScheduleUpdateRequest;
import com.mycom.myapp.schedule.dto.response.SchedulePageResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

    @ParameterizedTest
    @EnumSource(GroupRole.class)
    void activeMemberOfAnyRoleCanReadUpcomingSchedules(GroupRole role) {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, role));
        PageRequest pageable = PageRequest.of(0, 20);
        StudySchedule schedule = schedule(group, 100L, NOW.plusDays(1));
        when(scheduleRepository
                        .findAllByStudyGroupIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(
                                10L, NOW, pageable))
                .thenReturn(new PageImpl<>(List.of(schedule), pageable, 1));

        SchedulePageResponse response =
                service.getSchedules(10L, 1L, ScheduleScope.UPCOMING, 0, 20);

        assertThat(response.items()).extracting(item -> item.scheduleId()).containsExactly(100L);
        verify(scheduleRepository)
                .findAllByStudyGroupIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(
                        10L, NOW, pageable);
    }

    @Test
    void activeMemberCanReadPastSchedulesFromEndedGroup() {
        StudyGroup group = activeGroup();
        group.end();
        allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));
        PageRequest pageable = PageRequest.of(1, 10);
        when(scheduleRepository
                        .findAllByStudyGroupIdAndScheduledAtLessThanOrderByScheduledAtDescIdDesc(
                                10L, NOW, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        SchedulePageResponse response = service.getSchedules(10L, 1L, ScheduleScope.PAST, 1, 10);

        assertThat(response.items()).isEmpty();
        verify(scheduleRepository)
                .findAllByStudyGroupIdAndScheduledAtLessThanOrderByScheduledAtDescIdDesc(
                        10L, NOW, pageable);
    }

    @Test
    void rejectsScheduleListForMissingGroupBeforeMembershipLookup() {
        when(groupRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSchedules(10L, 1L, ScheduleScope.UPCOMING, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.GROUP_NOT_FOUND));
        verifyNoInteractions(memberRepository, scheduleRepository);
    }

    @Test
    void rejectsScheduleListForMissingMembership() {
        StudyGroup group = activeGroup();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(memberRepository.findByStudyGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSchedules(10L, 1L, ScheduleScope.UPCOMING, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.GROUP_ACCESS_DENIED));
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void rejectsScheduleListForWithdrawnMember() {
        StudyGroup group = activeGroup();
        GroupMember member = GroupMember.join(group, 1L, GroupRole.MEMBER);
        member.withdraw();
        allow(group, member);

        assertThatThrownBy(() -> service.getSchedules(10L, 1L, ScheduleScope.PAST, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.WITHDRAWN_GROUP_MEMBER));
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void returnsFullScheduleDetailForActiveMember() {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));
        StudySchedule schedule = schedule(group, 100L, NOW.minusDays(1));
        when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
                .thenReturn(Optional.of(schedule));

        ScheduleResponse response = service.getSchedule(10L, 1L, 100L);

        assertThat(response.scheduleId()).isEqualTo(100L);
        assertThat(response.groupId()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("조회 일정");
    }

    @Test
    void reportsScheduleNotFoundWhenScheduleIsMissingOrBelongsToAnotherGroup() {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));
        when(scheduleRepository.findByIdAndStudyGroupId(999L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSchedule(10L, 1L, 999L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    @ParameterizedTest
    @EnumSource(
            value = GroupRole.class,
            names = {"LEADER", "MANAGER"})
    void updatesFutureScheduleForManagementRole(GroupRole role) {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, role));
        LocalDateTime responseDeadline = NOW.minusHours(1);
        StudySchedule schedule = schedule(group, 100L, NOW.plusHours(2), responseDeadline);
        when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
                .thenReturn(Optional.of(schedule));

        ScheduleResponse response = service.update(10L, 1L, 100L, updateRequest(NOW.plusHours(3)));

        assertThat(response.title()).isEqualTo("수정 일정");
        assertThat(response.scheduledAt()).isEqualTo(NOW.plusHours(3));
        assertThat(response.location()).isEqualTo("수정 장소");
        assertThat(response.responseDeadline()).isEqualTo(responseDeadline);
        assertThat(response.creatorId()).isEqualTo(1L);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        verify(scheduleRepository).findByIdAndStudyGroupId(100L, 10L);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void rejectsUpdateForEndedGroupBeforeRoleAndScheduleLookup() {
        StudyGroup group = activeGroup();
        group.end();
        allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));

        assertUpdateError(ErrorCode.GROUP_ENDED, updateRequest(NOW.plusHours(3)));
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void rejectsUpdateForMemberBeforeScheduleLookup() {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));

        assertUpdateError(ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN, updateRequest(NOW.plusHours(3)));
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void reportsScheduleNotFoundBeforeTimeValidation() {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
        when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L)).thenReturn(Optional.empty());

        assertUpdateError(ErrorCode.SCHEDULE_NOT_FOUND, updateRequest(NOW));
    }

    @ParameterizedTest
    @MethodSource("startedScheduleTimes")
    void rejectsStartedScheduleWithStateConflict(LocalDateTime existingScheduledAt) {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
        when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
                .thenReturn(Optional.of(schedule(group, 100L, existingScheduledAt, null)));

        assertUpdateError(ErrorCode.SCHEDULE_UPDATE_NOT_ALLOWED, updateRequest(NOW.plusHours(3)));
    }

    @ParameterizedTest
    @MethodSource("invalidNewScheduleTimes")
    void rejectsNewScheduleTimeAtOrBeforeNow(LocalDateTime requestedScheduledAt) {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
        when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
                .thenReturn(Optional.of(schedule(group, 100L, NOW.plusHours(2), null)));

        assertUpdateError(ErrorCode.INVALID_SCHEDULE_TIME, updateRequest(requestedScheduledAt));
    }

    @Test
    void rejectsNewScheduleTimeBeforePreservedDeadline() {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
        when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
                .thenReturn(Optional.of(schedule(group, 100L, NOW.plusHours(4), NOW.plusHours(3))));

        assertUpdateError(ErrorCode.INVALID_SCHEDULE_TIME, updateRequest(NOW.plusHours(2)));
    }

    @Test
    void allowsNewScheduleTimeEqualToPreservedDeadline() {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
        LocalDateTime deadline = NOW.plusHours(3);
        when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
                .thenReturn(Optional.of(schedule(group, 100L, NOW.plusHours(4), deadline)));

        ScheduleResponse response = service.update(10L, 1L, 100L, updateRequest(deadline));

        assertThat(response.scheduledAt()).isEqualTo(deadline);
        assertThat(response.responseDeadline()).isEqualTo(deadline);
    }

    @Test
    void rejectsUpdateForMissingGroupBeforeMembershipLookup() {
        when(groupRepository.findById(10L)).thenReturn(Optional.empty());

        assertUpdateError(ErrorCode.GROUP_NOT_FOUND, updateRequest(NOW.plusHours(3)));
        verifyNoInteractions(memberRepository, scheduleRepository);
    }

    @Test
    void rejectsUpdateForMissingMembershipBeforeScheduleLookup() {
        StudyGroup group = activeGroup();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(memberRepository.findByStudyGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertUpdateError(ErrorCode.GROUP_ACCESS_DENIED, updateRequest(NOW.plusHours(3)));
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void rejectsUpdateForWithdrawnMemberBeforeScheduleLookup() {
        StudyGroup group = activeGroup();
        GroupMember member = GroupMember.join(group, 1L, GroupRole.LEADER);
        member.withdraw();
        allow(group, member);

        assertUpdateError(ErrorCode.WITHDRAWN_GROUP_MEMBER, updateRequest(NOW.plusHours(3)));
        verifyNoInteractions(scheduleRepository);
    }

    private static Stream<ScheduleCreateRequest> invalidTimeRequests() {
        return Stream.of(
                request(NOW, null),
                request(NOW.minusSeconds(1), null),
                request(NOW.plusHours(2), NOW),
                request(NOW.plusHours(2), NOW.plusHours(3)));
    }

    private static Stream<LocalDateTime> startedScheduleTimes() {
        return Stream.of(NOW, NOW.minusSeconds(1));
    }

    private static Stream<LocalDateTime> invalidNewScheduleTimes() {
        return Stream.of(NOW, NOW.minusSeconds(1));
    }

    private StudySchedule schedule(StudyGroup group, Long scheduleId, LocalDateTime scheduledAt) {
        return schedule(group, scheduleId, scheduledAt, null);
    }

    private StudySchedule schedule(
            StudyGroup group,
            Long scheduleId,
            LocalDateTime scheduledAt,
            LocalDateTime responseDeadline) {
        StudySchedule schedule =
                StudySchedule.create(
                        group, 1L, "조회 일정", scheduledAt, null, null, null, null, responseDeadline);
        ReflectionTestUtils.setField(schedule, "id", scheduleId);
        ReflectionTestUtils.setField(schedule, "createdAt", NOW.minusDays(1));
        ReflectionTestUtils.setField(schedule, "updatedAt", NOW.minusDays(1));
        return schedule;
    }

    private ScheduleUpdateRequest updateRequest(LocalDateTime scheduledAt) {
        return new ScheduleUpdateRequest("수정 일정", scheduledAt, "수정 장소", null, "수정 내용", null);
    }

    private void assertError(ErrorCode errorCode, ScheduleCreateRequest request) {
        assertThatThrownBy(() -> service.create(10L, 1L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
        verify(scheduleRepository, never()).save(any());
    }

    private void assertUpdateError(ErrorCode errorCode, ScheduleUpdateRequest request) {
        assertThatThrownBy(() -> service.update(10L, 1L, 100L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
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
