package com.mycom.myapp.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mycom.myapp.attendance.dto.request.AttendanceAnswerRequest;
import com.mycom.myapp.attendance.dto.request.AttendanceCheckRequest;
import com.mycom.myapp.attendance.dto.response.AttendanceAnswerSummaryResponse;
import com.mycom.myapp.attendance.dto.response.AttendanceSummaryResponse;
import com.mycom.myapp.attendance.dto.response.MyAttendanceRateResponse;
import com.mycom.myapp.attendance.entity.AttendanceAnswer;
import com.mycom.myapp.attendance.entity.AttendanceRecord;
import com.mycom.myapp.attendance.entity.AttendanceResponse;
import com.mycom.myapp.attendance.entity.AttendanceStatus;
import com.mycom.myapp.attendance.repository.AttendanceRecordRepository;
import com.mycom.myapp.attendance.repository.AttendanceResponseRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.schedule.service.ScheduleAttendancePolicy;
import com.mycom.myapp.schedule.service.port.ScheduleAttendancePolicyReader;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.GroupStatus;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.service.StudyGroupAttendanceRatePolicy;
import com.mycom.myapp.study.service.port.StudyGroupAttendanceRatePolicyReader;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 24, 12, 0);

    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private AttendanceResponseRepository attendanceResponseRepository;
    @Mock private StudyScheduleRepository studyScheduleRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private ScheduleAttendancePolicyReader scheduleAttendancePolicyReader;
    @Mock private StudyGroupAttendanceRatePolicyReader studyGroupAttendanceRatePolicyReader;

    @InjectMocks private AttendanceService attendanceService;

    @BeforeEach
    void fixClock() {
        ReflectionTestUtils.setField(
                attendanceService, "clock", Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    @Test
    void submitAnswerSavesNewAnswer() {
        stubOpenPolicy(10L, 20L);
        given(attendanceResponseRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.empty());
        given(attendanceResponseRepository.save(any(AttendanceAnswer.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AttendanceAnswer result =
                attendanceService.submitAnswer(
                        10L, 20L, new AttendanceAnswerRequest(AttendanceResponse.ATTEND));

        assertThat(result.getScheduleId()).isEqualTo(10L);
        assertThat(result.getUserId()).isEqualTo(20L);
        assertThat(result.getResponse()).isEqualTo(AttendanceResponse.ATTEND);
    }

    @Test
    void submitAnswerRejectsDuplicateAnswer() {
        stubOpenPolicy(10L, 20L);
        AttendanceAnswer existing =
                AttendanceAnswer.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .response(AttendanceResponse.ATTEND)
                        .build();
        given(attendanceResponseRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                attendanceService.submitAnswer(
                                        10L,
                                        20L,
                                        new AttendanceAnswerRequest(AttendanceResponse.ATTEND)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_ATTENDANCE_ANSWER));
    }

    @Test
    void submitAnswerThrowsWhenResponseClosed() {
        stubClosedPolicy(10L, 20L);

        assertThatThrownBy(
                        () ->
                                attendanceService.submitAnswer(
                                        10L,
                                        20L,
                                        new AttendanceAnswerRequest(AttendanceResponse.ATTEND)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_RESPONSE_CLOSED));
    }

    @Test
    void changeAnswerUpdatesExistingAnswer() {
        stubOpenPolicy(10L, 20L);
        AttendanceAnswer existing =
                AttendanceAnswer.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .response(AttendanceResponse.UNDECIDED)
                        .build();
        given(attendanceResponseRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.of(existing));

        AttendanceAnswer result =
                attendanceService.changeAnswer(
                        10L, 20L, new AttendanceAnswerRequest(AttendanceResponse.ATTEND));

        assertThat(result.getResponse()).isEqualTo(AttendanceResponse.ATTEND);
    }

    @Test
    void changeAnswerThrowsWhenAnswerNotFound() {
        stubOpenPolicy(10L, 20L);
        given(attendanceResponseRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                attendanceService.changeAnswer(
                                        10L,
                                        20L,
                                        new AttendanceAnswerRequest(AttendanceResponse.ATTEND)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_ANSWER_NOT_FOUND));
    }

    @Test
    void changeAnswerThrowsWhenResponseClosed() {
        stubClosedPolicy(10L, 20L);

        assertThatThrownBy(
                        () ->
                                attendanceService.changeAnswer(
                                        10L,
                                        20L,
                                        new AttendanceAnswerRequest(AttendanceResponse.ATTEND)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_RESPONSE_CLOSED));
    }

    @Test
    void deleteAnswerDeletesExistingAnswer() {
        stubOpenPolicy(10L, 20L);
        AttendanceAnswer existing =
                AttendanceAnswer.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .response(AttendanceResponse.ATTEND)
                        .build();
        given(attendanceResponseRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.of(existing));

        attendanceService.deleteAnswer(10L, 20L);

        verify(attendanceResponseRepository).delete(existing);
    }

    @Test
    void deleteAnswerThrowsWhenResponseClosed() {
        stubClosedPolicy(10L, 20L);

        assertThatThrownBy(() -> attendanceService.deleteAnswer(10L, 20L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_RESPONSE_CLOSED));
    }

    @Test
    void checkAttendanceSavesNewRecord() {
        stubManager(10L, 1L, GroupRole.LEADER);
        given(attendanceRecordRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.empty());
        given(attendanceRecordRepository.save(any(AttendanceRecord.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AttendanceRecord result =
                attendanceService.checkAttendance(
                        10L, 1L, new AttendanceCheckRequest(20L, AttendanceStatus.PRESENT));

        assertThat(result.getScheduleId()).isEqualTo(10L);
        assertThat(result.getUserId()).isEqualTo(20L);
        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.getCheckedBy()).isEqualTo(1L);
    }

    @Test
    void checkAttendanceRejectsDuplicateRecord() {
        stubManager(10L, 1L, GroupRole.MANAGER);
        AttendanceRecord existing =
                AttendanceRecord.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .status(AttendanceStatus.PRESENT)
                        .checkedBy(1L)
                        .build();
        given(attendanceRecordRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                attendanceService.checkAttendance(
                                        10L,
                                        1L,
                                        new AttendanceCheckRequest(20L, AttendanceStatus.PRESENT)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_ATTENDANCE_RECORD));
    }

    @Test
    void checkAttendanceThrowsWhenRequesterIsPlainMember() {
        stubManager(10L, 1L, GroupRole.MEMBER);

        assertThatThrownBy(
                        () ->
                                attendanceService.checkAttendance(
                                        10L,
                                        1L,
                                        new AttendanceCheckRequest(20L, AttendanceStatus.PRESENT)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN));
    }

    @Test
    void checkAttendanceThrowsWhenScheduleNotFound() {
        given(studyScheduleRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                attendanceService.checkAttendance(
                                        10L,
                                        1L,
                                        new AttendanceCheckRequest(20L, AttendanceStatus.PRESENT)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    @Test
    void checkAttendanceThrowsWhenNotGroupMember() {
        StudyGroup group = group();
        given(studyScheduleRepository.findById(10L)).willReturn(Optional.of(schedule(group, 10L)));
        given(groupMemberRepository.findByStudyGroupIdAndUserId(100L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                attendanceService.checkAttendance(
                                        10L,
                                        1L,
                                        new AttendanceCheckRequest(20L, AttendanceStatus.PRESENT)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.GROUP_ACCESS_DENIED));
    }

    @Test
    void checkAttendanceThrowsWhenMemberIsWithdrawn() {
        StudyGroup group = group();
        GroupMember member = GroupMember.join(group, 1L, GroupRole.LEADER);
        member.withdraw();
        given(studyScheduleRepository.findById(10L)).willReturn(Optional.of(schedule(group, 10L)));
        given(groupMemberRepository.findByStudyGroupIdAndUserId(100L, 1L))
                .willReturn(Optional.of(member));

        assertThatThrownBy(
                        () ->
                                attendanceService.checkAttendance(
                                        10L,
                                        1L,
                                        new AttendanceCheckRequest(20L, AttendanceStatus.PRESENT)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.WITHDRAWN_GROUP_MEMBER));
    }

    @Test
    void updateAttendanceUpdatesExistingRecord() {
        stubManager(10L, 2L, GroupRole.LEADER);
        AttendanceRecord existing =
                AttendanceRecord.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .status(AttendanceStatus.ABSENT)
                        .checkedBy(1L)
                        .build();
        given(attendanceRecordRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.of(existing));

        AttendanceRecord result =
                attendanceService.updateAttendance(
                        10L, 2L, new AttendanceCheckRequest(20L, AttendanceStatus.LATE));

        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(result.getCheckedBy()).isEqualTo(2L);
    }

    @Test
    void updateAttendanceThrowsWhenRecordNotFound() {
        stubManager(10L, 2L, GroupRole.MANAGER);
        given(attendanceRecordRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                attendanceService.updateAttendance(
                                        10L,
                                        2L,
                                        new AttendanceCheckRequest(20L, AttendanceStatus.LATE)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_RECORD_NOT_FOUND));
    }

    @Test
    void updateAttendanceThrowsWhenRequesterIsPlainMember() {
        stubManager(10L, 2L, GroupRole.MEMBER);

        assertThatThrownBy(
                        () ->
                                attendanceService.updateAttendance(
                                        10L,
                                        2L,
                                        new AttendanceCheckRequest(20L, AttendanceStatus.LATE)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN));
    }

    @Test
    void deleteAttendanceDeletesExistingRecord() {
        stubManager(10L, 1L, GroupRole.LEADER);
        AttendanceRecord existing =
                AttendanceRecord.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .status(AttendanceStatus.PRESENT)
                        .checkedBy(1L)
                        .build();
        given(attendanceRecordRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.of(existing));

        attendanceService.deleteAttendance(10L, 20L, 1L);

        verify(attendanceRecordRepository).delete(existing);
    }

    @Test
    void deleteAttendanceThrowsWhenRequesterIsPlainMember() {
        stubManager(10L, 1L, GroupRole.MEMBER);

        assertThatThrownBy(() -> attendanceService.deleteAttendance(10L, 20L, 1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN));
    }

    @Test
    void getSummaryAggregatesRecordsByStatus() {
        stubManager(10L, 1L, GroupRole.LEADER);
        List<AttendanceRecord> records =
                List.of(
                        AttendanceRecord.builder()
                                .scheduleId(10L)
                                .userId(20L)
                                .status(AttendanceStatus.PRESENT)
                                .checkedBy(1L)
                                .build(),
                        AttendanceRecord.builder()
                                .scheduleId(10L)
                                .userId(21L)
                                .status(AttendanceStatus.LATE)
                                .checkedBy(1L)
                                .build(),
                        AttendanceRecord.builder()
                                .scheduleId(10L)
                                .userId(22L)
                                .status(AttendanceStatus.ABSENT)
                                .checkedBy(1L)
                                .build());
        given(attendanceRecordRepository.findByScheduleId(10L)).willReturn(records);

        AttendanceSummaryResponse summary = attendanceService.getSummary(10L, 1L);

        assertThat(summary.getTotalCount()).isEqualTo(3);
        assertThat(summary.getPresentCount()).isEqualTo(1);
        assertThat(summary.getLateCount()).isEqualTo(1);
        assertThat(summary.getAbsentCount()).isEqualTo(1);
        assertThat(summary.getExcusedCount()).isEqualTo(0);
        assertThat(summary.getMembers()).hasSize(3);
    }

    @Test
    void getSummaryThrowsWhenRequesterIsPlainMember() {
        stubManager(10L, 1L, GroupRole.MEMBER);

        assertThatThrownBy(() -> attendanceService.getSummary(10L, 1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN));
    }

    @Test
    void getAnswerSummaryFillsUndecidedForMembersWithoutAnswer() {
        StudyGroup group = group();
        stubManager(10L, 1L, GroupRole.LEADER);
        AttendanceAnswer submittedAnswer =
                AttendanceAnswer.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .response(AttendanceResponse.ATTEND)
                        .build();
        given(attendanceResponseRepository.findByScheduleId(10L))
                .willReturn(List.of(submittedAnswer));
        given(
                        groupMemberRepository
                                .findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                                        100L, GroupMemberStatus.ACTIVE))
                .willReturn(
                        List.of(
                                GroupMember.join(group, 20L, GroupRole.MEMBER),
                                GroupMember.join(group, 21L, GroupRole.MEMBER)));

        AttendanceAnswerSummaryResponse summary = attendanceService.getAnswerSummary(10L, 1L);

        assertThat(summary.getTotalMemberCount()).isEqualTo(2);
        assertThat(summary.getAttendCount()).isEqualTo(1);
        assertThat(summary.getUndecidedCount()).isEqualTo(1);
        assertThat(summary.getAbsentCount()).isEqualTo(0);

        AttendanceAnswerSummaryResponse.MemberAnswer respondedMember =
                summary.getMembers().stream()
                        .filter(m -> m.getUserId().equals(20L))
                        .findFirst()
                        .orElseThrow();
        assertThat(respondedMember.getResponse()).isEqualTo(AttendanceResponse.ATTEND);
        assertThat(respondedMember.getRespondedAt()).isNotNull();

        AttendanceAnswerSummaryResponse.MemberAnswer unrespondedMember =
                summary.getMembers().stream()
                        .filter(m -> m.getUserId().equals(21L))
                        .findFirst()
                        .orElseThrow();
        assertThat(unrespondedMember.getResponse()).isEqualTo(AttendanceResponse.UNDECIDED);
        assertThat(unrespondedMember.getRespondedAt()).isNull();
    }

    @Test
    void getAnswerSummaryThrowsWhenRequesterIsPlainMember() {
        stubManager(10L, 1L, GroupRole.MEMBER);

        assertThatThrownBy(() -> attendanceService.getAnswerSummary(10L, 1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN));
    }

    @Test
    void getMyAttendanceRateComputesPercentageFromPresentCount() {
        given(attendanceRecordRepository.countByUserIdAndStatus(20L, AttendanceStatus.PRESENT))
                .willReturn(3L);
        given(attendanceRecordRepository.countByUserIdAndStatus(20L, AttendanceStatus.LATE))
                .willReturn(1L);
        given(attendanceRecordRepository.countByUserIdAndStatus(20L, AttendanceStatus.ABSENT))
                .willReturn(1L);
        given(attendanceRecordRepository.countByUserIdAndStatus(20L, AttendanceStatus.EXCUSED))
                .willReturn(0L);

        MyAttendanceRateResponse response = attendanceService.getMyAttendanceRate(20L, 20L);

        assertThat(response.getTotalCount()).isEqualTo(5);
        assertThat(response.getAttendanceRate()).isEqualTo(60.0);
    }

    @Test
    void getMyAttendanceRateReturnsZeroWhenNoRecords() {
        given(attendanceRecordRepository.countByUserIdAndStatus(any(), any())).willReturn(0L);

        MyAttendanceRateResponse response = attendanceService.getMyAttendanceRate(20L, 20L);

        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getAttendanceRate()).isEqualTo(0.0);
    }

    @Test
    void getMyAttendanceRateThrowsWhenRequesterIsNotSelf() {
        assertThatThrownBy(() -> attendanceService.getMyAttendanceRate(20L, 21L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_RATE_ACCESS_DENIED));
    }

    @Test
    void getGroupAttendanceRatesReturnsPerMemberRatesScopedToGroupSchedules() {
        StudyGroup group = group();
        stubCanViewAllAttendanceRates(100L, 1L, true);
        given(studyScheduleRepository.findAllByStudyGroupIdOrderByScheduledAtAsc(100L))
                .willReturn(List.of(schedule(group, 10L), schedule(group, 11L)));
        List<AttendanceRecord> records =
                List.of(
                        AttendanceRecord.builder()
                                .scheduleId(10L)
                                .userId(20L)
                                .status(AttendanceStatus.PRESENT)
                                .checkedBy(1L)
                                .build(),
                        AttendanceRecord.builder()
                                .scheduleId(11L)
                                .userId(20L)
                                .status(AttendanceStatus.ABSENT)
                                .checkedBy(1L)
                                .build(),
                        AttendanceRecord.builder()
                                .scheduleId(10L)
                                .userId(21L)
                                .status(AttendanceStatus.PRESENT)
                                .checkedBy(1L)
                                .build());
        given(attendanceRecordRepository.findByScheduleIdIn(List.of(10L, 11L))).willReturn(records);
        given(
                        groupMemberRepository
                                .findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                                        100L, GroupMemberStatus.ACTIVE))
                .willReturn(
                        List.of(
                                GroupMember.join(group, 20L, GroupRole.MEMBER),
                                GroupMember.join(group, 21L, GroupRole.MEMBER)));

        List<MyAttendanceRateResponse> result = attendanceService.getGroupAttendanceRates(100L, 1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(20L);
        assertThat(result.get(0).getPresentCount()).isEqualTo(1);
        assertThat(result.get(0).getAbsentCount()).isEqualTo(1);
        assertThat(result.get(0).getAttendanceRate()).isEqualTo(50.0);
        assertThat(result.get(1).getUserId()).isEqualTo(21L);
        assertThat(result.get(1).getPresentCount()).isEqualTo(1);
        assertThat(result.get(1).getAttendanceRate()).isEqualTo(100.0);
    }

    @Test
    void getGroupAttendanceRatesSkipsScheduleQueryWhenGroupHasNoSchedules() {
        StudyGroup group = group();
        stubCanViewAllAttendanceRates(100L, 1L, true);
        given(studyScheduleRepository.findAllByStudyGroupIdOrderByScheduledAtAsc(100L))
                .willReturn(List.of());
        given(
                        groupMemberRepository
                                .findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                                        100L, GroupMemberStatus.ACTIVE))
                .willReturn(List.of(GroupMember.join(group, 20L, GroupRole.MEMBER)));

        List<MyAttendanceRateResponse> result = attendanceService.getGroupAttendanceRates(100L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalCount()).isEqualTo(0);
        assertThat(result.get(0).getAttendanceRate()).isEqualTo(0.0);
        verify(attendanceRecordRepository, never()).findByScheduleIdIn(any());
    }

    @Test
    void getGroupAttendanceRatesThrowsWhenPolicyForbids() {
        stubCanViewAllAttendanceRates(100L, 1L, false);

        assertThatThrownBy(() -> attendanceService.getGroupAttendanceRates(100L, 1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN));
    }

    @Test
    void getGroupAttendanceRatesPropagatesPolicyReaderErrors() {
        given(studyGroupAttendanceRatePolicyReader.getAttendanceRatePolicy(100L, 1L))
                .willThrow(new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));

        assertThatThrownBy(() -> attendanceService.getGroupAttendanceRates(100L, 1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.GROUP_ACCESS_DENIED));
    }

    private void stubCanViewAllAttendanceRates(
            Long groupId, Long requesterId, boolean canViewAllAttendanceRates) {
        given(studyGroupAttendanceRatePolicyReader.getAttendanceRatePolicy(groupId, requesterId))
                .willReturn(new StudyGroupAttendanceRatePolicy(groupId, canViewAllAttendanceRates));
    }

    private void stubOpenPolicy(Long scheduleId, Long userId) {
        given(scheduleAttendancePolicyReader.getAttendancePolicy(scheduleId, userId))
                .willReturn(
                        new ScheduleAttendancePolicy(
                                scheduleId, 100L, GroupStatus.ACTIVE, true, NOW.plusDays(1), null));
    }

    private void stubClosedPolicy(Long scheduleId, Long userId) {
        given(scheduleAttendancePolicyReader.getAttendancePolicy(scheduleId, userId))
                .willReturn(
                        new ScheduleAttendancePolicy(
                                scheduleId,
                                100L,
                                GroupStatus.ACTIVE,
                                true,
                                NOW.minusHours(1),
                                null));
    }

    private void stubManager(Long scheduleId, Long userId, GroupRole role) {
        StudyGroup group = group();
        given(studyScheduleRepository.findById(scheduleId))
                .willReturn(Optional.of(schedule(group, scheduleId)));
        given(groupMemberRepository.findByStudyGroupIdAndUserId(100L, userId))
                .willReturn(Optional.of(GroupMember.join(group, userId, role)));
    }

    private StudyGroup group() {
        StudyGroup group = StudyGroup.create(25L, "스터디");
        ReflectionTestUtils.setField(group, "id", 100L);
        return group;
    }

    private StudySchedule schedule(StudyGroup group, Long scheduleId) {
        StudySchedule schedule =
                StudySchedule.create(
                        group,
                        1L,
                        "일정",
                        LocalDateTime.of(2026, 7, 25, 19, 0),
                        null,
                        null,
                        null,
                        null,
                        null);
        ReflectionTestUtils.setField(schedule, "id", scheduleId);
        return schedule;
    }
}
