package com.mycom.myapp.attendance.service;

import com.mycom.myapp.attendance.dto.request.AttendanceAnswerRequest;
import com.mycom.myapp.attendance.dto.request.AttendanceCheckRequest;
import com.mycom.myapp.attendance.dto.response.AttendanceSummaryResponse;
import com.mycom.myapp.attendance.dto.response.MyAttendanceRateResponse;
import com.mycom.myapp.attendance.entity.AttendanceAnswer;
import com.mycom.myapp.attendance.entity.AttendanceRecord;
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
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참석 여부 응답(AttendanceAnswer)과 출석 체크(AttendanceRecord)를 함께 다루는 서비스. 응답은 멤버 본인이, 체크는 모집장이 남긴다는 점에서
 * 주체가 다르지만 둘 다 (scheduleId, userId) 조합으로 단건이 결정되는 동일한 패턴이라 한 클래스에서 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceResponseRepository attendanceResponseRepository;
    private final StudyScheduleRepository studyScheduleRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ScheduleAttendancePolicyReader scheduleAttendancePolicyReader;
    private final StudyGroupAttendanceRatePolicyReader studyGroupAttendanceRatePolicyReader;
    private final Clock clock;

    /** 멤버가 스케줄에 처음 참석 여부를 등록한다. 마감이 지났거나 이미 응답이 있으면 거부한다. */
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

    /** 이미 등록된 참석 여부 응답을 바꾼다. (예: UNDECIDED → ATTEND) 마감이 지나면 거부한다. */
    @Transactional
    public AttendanceAnswer changeAnswer(
            Long scheduleId, Long userId, AttendanceAnswerRequest request) {
        validateResponseOpen(scheduleId, userId);
        AttendanceAnswer answer = getAnswer(scheduleId, userId);
        answer.changeResponse(request.getResponse());
        return answer;
    }

    /** 참석 여부 응답을 삭제한다. 마감이 지나면 거부한다. */
    @Transactional
    public void deleteAnswer(Long scheduleId, Long userId) {
        validateResponseOpen(scheduleId, userId);
        attendanceResponseRepository.delete(getAnswer(scheduleId, userId));
    }

    /** 모집장이 스케줄의 특정 멤버 출석 상태를 처음 체크한다. 이미 체크된 기록이 있으면 거부한다. */
    @Transactional
    public AttendanceRecord checkAttendance(
            Long scheduleId, Long checkedBy, AttendanceCheckRequest request) {
        validateManager(scheduleId, checkedBy);
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

    /** 이미 체크된 출석 상태를 수정한다. checkedBy/checkedAt도 함께 갱신된다. */
    @Transactional
    public AttendanceRecord updateAttendance(
            Long scheduleId, Long checkedBy, AttendanceCheckRequest request) {
        validateManager(scheduleId, checkedBy);
        AttendanceRecord record = getRecord(scheduleId, request.getUserId());
        record.updateStatus(request.getStatus(), checkedBy);
        return record;
    }

    /** 모집장이 출석 체크 기록을 삭제한다. */
    @Transactional
    public void deleteAttendance(Long scheduleId, Long userId, Long requesterId) {
        validateManager(scheduleId, requesterId);
        attendanceRecordRepository.delete(getRecord(scheduleId, userId));
    }

    /** 모집장이 스케줄 하나의 출석 현황(상태별 인원 수 + 멤버별 내역)을 조회한다. */
    public AttendanceSummaryResponse getSummary(Long scheduleId, Long requesterId) {
        validateManager(scheduleId, requesterId);
        List<AttendanceRecord> records = attendanceRecordRepository.findByScheduleId(scheduleId);
        return AttendanceSummaryResponse.of(scheduleId, records);
    }

    /** 본인의 전체 스케줄 기준 누적 출석률(PRESENT 건수 / 전체 건수 * 100)을 계산한다. */
    public MyAttendanceRateResponse getMyAttendanceRate(Long userId, Long requesterId) {
        if (!requesterId.equals(userId)) {
            throw new BusinessException(ErrorCode.ATTENDANCE_RATE_ACCESS_DENIED);
        }
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

    /** 그룹의 활성 LEADER/MANAGER가 그룹원별 출석률(이 그룹의 스케줄만 집계)을 조회한다. */
    public List<MyAttendanceRateResponse> getGroupAttendanceRates(Long groupId, Long requesterId) {
        StudyGroupAttendanceRatePolicy policy =
                studyGroupAttendanceRatePolicyReader.getAttendanceRatePolicy(groupId, requesterId);
        if (!policy.canViewAllAttendanceRates()) {
            throw new BusinessException(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN);
        }

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

    /** (scheduleId, userId) 조합의 참석 여부 응답을 찾는다. 없으면 예외. */
    private AttendanceAnswer getAnswer(Long scheduleId, Long userId) {
        return attendanceResponseRepository
                .findByScheduleIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_ANSWER_NOT_FOUND));
    }

    /** (scheduleId, userId) 조합의 출석 체크 기록을 찾는다. 없으면 예외. */
    private AttendanceRecord getRecord(Long scheduleId, Long userId) {
        return attendanceRecordRepository
                .findByScheduleIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_RECORD_NOT_FOUND));
    }

    /** checkedBy가 scheduleId 소속 그룹의 활성 LEADER/MANAGER인지 검증한다. */
    private void validateManager(Long scheduleId, Long checkedBy) {
        GroupMember member = validateActiveMember(scheduleId, checkedBy);
        if (member.getRole() != GroupRole.LEADER && member.getRole() != GroupRole.MANAGER) {
            throw new BusinessException(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN);
        }
    }

    /** userId가 scheduleId 소속 그룹의 활성 그룹원인지(역할 무관) 검증한다. */
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

    /**
     * userId가 scheduleId 소속 그룹의 활성 그룹원인지 확인하고, 참석 응답 마감(effectiveDeadline)이 지나지 않았는지 검증한다. Part3의
     * 공개 포트({@link ScheduleAttendancePolicyReader})를 통해 조회한다.
     */
    private void validateResponseOpen(Long scheduleId, Long userId) {
        ScheduleAttendancePolicy policy =
                scheduleAttendancePolicyReader.getAttendancePolicy(scheduleId, userId);
        if (!LocalDateTime.now(clock).isBefore(policy.effectiveDeadline())) {
            throw new BusinessException(ErrorCode.ATTENDANCE_RESPONSE_CLOSED);
        }
    }
}
