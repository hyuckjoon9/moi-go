package com.mycom.myapp.attendance.service;

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
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.service.StudyGroupAttendanceRatePolicy;
import com.mycom.myapp.study.service.port.StudyGroupAttendanceRatePolicyReader;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private static final Duration AUTO_ABSENCE_WINDOW = Duration.ofHours(2);

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceResponseRepository attendanceResponseRepository;
    private final StudyScheduleRepository studyScheduleRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ScheduleAttendancePolicyReader scheduleAttendancePolicyReader;
    private final StudyGroupAttendanceRatePolicyReader studyGroupAttendanceRatePolicyReader;
    private final Clock clock;

    @Transactional
    public AttendanceAnswer submitAnswer(
            Long scheduleId, Long userId, AttendanceAnswerRequest request) {
        validateResponseOpen(scheduleId, userId);
        if (attendanceResponseRepository
                .findByScheduleIdAndUserId(scheduleId, userId)
                .isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ATTENDANCE_ANSWER);
        }
        AttendanceAnswer answer =
                AttendanceAnswer.builder()
                        .scheduleId(scheduleId)
                        .userId(userId)
                        .response(request.getResponse())
                        .build();
        return attendanceResponseRepository.save(answer);
    }

    @Transactional
    public AttendanceAnswer changeAnswer(
            Long scheduleId, Long userId, AttendanceAnswerRequest request) {
        validateResponseOpen(scheduleId, userId);
        AttendanceAnswer answer = getAnswer(scheduleId, userId);
        answer.changeResponse(request.getResponse());
        return answer;
    }

    @Transactional
    public void deleteAnswer(Long scheduleId, Long userId) {
        validateResponseOpen(scheduleId, userId);
        attendanceResponseRepository.delete(getAnswer(scheduleId, userId));
    }

    @Transactional
    public AttendanceRecord checkAttendance(
            Long scheduleId, Long checkedBy, AttendanceCheckRequest request) {
        validateManager(scheduleId, checkedBy);
        validateAttendanceStarted(scheduleId);
        if (attendanceRecordRepository
                .findByScheduleIdAndUserId(scheduleId, request.getUserId())
                .isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ATTENDANCE_RECORD);
        }
        AttendanceRecord record =
                AttendanceRecord.builder()
                        .scheduleId(scheduleId)
                        .userId(request.getUserId())
                        .status(request.getStatus())
                        .checkedBy(checkedBy)
                        .build();
        return attendanceRecordRepository.save(record);
    }

    @Transactional
    public AttendanceRecord updateAttendance(
            Long scheduleId, Long checkedBy, AttendanceCheckRequest request) {
        validateManager(scheduleId, checkedBy);
        AttendanceRecord record = getRecord(scheduleId, request.getUserId());
        record.updateStatus(request.getStatus(), checkedBy);
        return record;
    }

    @Transactional
    public void deleteAttendance(Long scheduleId, Long userId, Long requesterId) {
        validateManager(scheduleId, requesterId);
        attendanceRecordRepository.delete(getRecord(scheduleId, userId));
    }

    @Transactional
    public void autoProcessOverdueAttendance() {
        LocalDateTime windowEnd = LocalDateTime.now(clock).minus(AUTO_ABSENCE_WINDOW);
        studyScheduleRepository.findAll().stream()
                .filter(schedule -> !schedule.getScheduledAt().isAfter(windowEnd))
                .forEach(this::autoProcessSchedule);
    }

    private void autoProcessSchedule(StudySchedule schedule) {
        Long groupId = schedule.getStudyGroup().getId();
        List<GroupMember> members =
                groupMemberRepository
                        .findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                                groupId, GroupMemberStatus.ACTIVE);
        if (members.isEmpty()) {
            return;
        }

        Set<Long> checkedUserIds =
                attendanceRecordRepository.findByScheduleId(schedule.getId()).stream()
                        .map(AttendanceRecord::getUserId)
                        .collect(Collectors.toSet());
        Map<Long, AttendanceResponse> responseByUserId =
                attendanceResponseRepository.findByScheduleId(schedule.getId()).stream()
                        .collect(
                                Collectors.toMap(
                                        AttendanceAnswer::getUserId,
                                        AttendanceAnswer::getResponse));

        for (GroupMember member : members) {
            Long userId = member.getUserId();
            if (checkedUserIds.contains(userId)) {
                continue;
            }
            AttendanceStatus status =
                    responseByUserId.get(userId) == AttendanceResponse.ABSENT
                            ? AttendanceStatus.EXCUSED
                            : AttendanceStatus.ABSENT;
            attendanceRecordRepository.save(
                    AttendanceRecord.builder()
                            .scheduleId(schedule.getId())
                            .userId(userId)
                            .status(status)
                            .checkedBy(null)
                            .build());
        }
    }

    @Transactional
    public AttendanceSummaryResponse getSummary(Long scheduleId, Long requesterId) {
        validateManager(scheduleId, requesterId);
        autoProcessOverdueAttendance();
        List<AttendanceRecord> records = attendanceRecordRepository.findByScheduleId(scheduleId);
        return AttendanceSummaryResponse.of(scheduleId, records);
    }

    public AttendanceAnswerSummaryResponse getAnswerSummary(Long scheduleId, Long requesterId) {
        validateManager(scheduleId, requesterId);
        StudySchedule schedule =
                studyScheduleRepository
                        .findById(scheduleId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        Long groupId = schedule.getStudyGroup().getId();
        List<GroupMember> members =
                groupMemberRepository
                        .findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                                groupId, GroupMemberStatus.ACTIVE);
        List<AttendanceAnswer> answers = attendanceResponseRepository.findByScheduleId(scheduleId);
        return AttendanceAnswerSummaryResponse.of(scheduleId, members, answers);
    }

    @Transactional
    public MyAttendanceRateResponse getMyAttendanceRate(Long userId, Long requesterId) {
        if (!requesterId.equals(userId)) {
            throw new BusinessException(ErrorCode.ATTENDANCE_RATE_ACCESS_DENIED);
        }
        autoProcessOverdueAttendance();
        long presentCount =
                attendanceRecordRepository.countByUserIdAndStatus(userId, AttendanceStatus.PRESENT);
        long lateCount =
                attendanceRecordRepository.countByUserIdAndStatus(userId, AttendanceStatus.LATE);
        long absentCount =
                attendanceRecordRepository.countByUserIdAndStatus(userId, AttendanceStatus.ABSENT);
        long excusedCount =
                attendanceRecordRepository.countByUserIdAndStatus(userId, AttendanceStatus.EXCUSED);
        return MyAttendanceRateResponse.of(
                userId, presentCount, lateCount, absentCount, excusedCount);
    }

    @Transactional
    public List<MyAttendanceRateResponse> getGroupAttendanceRates(Long groupId, Long requesterId) {
        StudyGroupAttendanceRatePolicy policy =
                studyGroupAttendanceRatePolicyReader.getAttendanceRatePolicy(groupId, requesterId);
        if (!policy.canViewAllAttendanceRates()) {
            throw new BusinessException(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN);
        }
        autoProcessOverdueAttendance();

        List<Long> scheduleIds =
                studyScheduleRepository.findAllByStudyGroupIdOrderByScheduledAtAsc(groupId).stream()
                        .map(StudySchedule::getId)
                        .toList();
        List<AttendanceRecord> records =
                scheduleIds.isEmpty()
                        ? List.of()
                        : attendanceRecordRepository.findByScheduleIdIn(scheduleIds);

        List<GroupMember> members =
                groupMemberRepository
                        .findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                                groupId, GroupMemberStatus.ACTIVE);
        return members.stream()
                .map(member -> memberAttendanceRate(member.getUserId(), records))
                .toList();
    }

    private MyAttendanceRateResponse memberAttendanceRate(
            Long userId, List<AttendanceRecord> groupRecords) {
        long presentCount = countStatus(groupRecords, userId, AttendanceStatus.PRESENT);
        long lateCount = countStatus(groupRecords, userId, AttendanceStatus.LATE);
        long absentCount = countStatus(groupRecords, userId, AttendanceStatus.ABSENT);
        long excusedCount = countStatus(groupRecords, userId, AttendanceStatus.EXCUSED);
        return MyAttendanceRateResponse.of(
                userId, presentCount, lateCount, absentCount, excusedCount);
    }

    private long countStatus(List<AttendanceRecord> records, Long userId, AttendanceStatus status) {
        return records.stream()
                .filter(record -> record.getUserId().equals(userId) && record.getStatus() == status)
                .count();
    }

    private AttendanceAnswer getAnswer(Long scheduleId, Long userId) {
        return attendanceResponseRepository
                .findByScheduleIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_ANSWER_NOT_FOUND));
    }

    private AttendanceRecord getRecord(Long scheduleId, Long userId) {
        return attendanceRecordRepository
                .findByScheduleIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_RECORD_NOT_FOUND));
    }

    private void validateManager(Long scheduleId, Long checkedBy) {
        GroupMember member = validateActiveMember(scheduleId, checkedBy);
        if (member.getRole() != GroupRole.LEADER && member.getRole() != GroupRole.MANAGER) {
            throw new BusinessException(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN);
        }
    }

    private void validateAttendanceStarted(Long scheduleId) {
        StudySchedule schedule =
                studyScheduleRepository
                        .findById(scheduleId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        if (LocalDateTime.now(clock).isBefore(schedule.getScheduledAt())) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHECK_NOT_STARTED);
        }
    }

    private GroupMember validateActiveMember(Long scheduleId, Long userId) {
        StudySchedule schedule =
                studyScheduleRepository
                        .findById(scheduleId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        Long groupId = schedule.getStudyGroup().getId();
        GroupMember member =
                groupMemberRepository
                        .findByStudyGroupIdAndUserId(groupId, userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));
        if (member.getStatus() == GroupMemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_GROUP_MEMBER);
        }
        return member;
    }

    private void validateResponseOpen(Long scheduleId, Long userId) {
        ScheduleAttendancePolicy policy =
                scheduleAttendancePolicyReader.getAttendancePolicy(scheduleId, userId);
        if (!LocalDateTime.now(clock).isBefore(policy.effectiveDeadline())) {
            throw new BusinessException(ErrorCode.ATTENDANCE_RESPONSE_CLOSED);
        }
    }
}
