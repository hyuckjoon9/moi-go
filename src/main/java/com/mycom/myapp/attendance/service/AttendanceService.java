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
import jakarta.persistence.EntityNotFoundException;
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

    /** 멤버가 스케줄에 처음 참석 여부를 등록한다. 이미 응답이 있으면 거부한다. */
    @Transactional
    public AttendanceAnswer submitAnswer(
            Long scheduleId, Long userId, AttendanceAnswerRequest request) {
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

    /** 이미 등록된 참석 여부 응답을 바꾼다. (예: UNDECIDED → ATTEND) */
    @Transactional
    public AttendanceAnswer changeAnswer(
            Long scheduleId, Long userId, AttendanceAnswerRequest request) {
        AttendanceAnswer answer = getAnswer(scheduleId, userId);
        answer.changeResponse(request.getResponse());
        return answer;
    }

    /** 참석 여부 응답을 삭제한다. */
    @Transactional
    public void deleteAnswer(Long scheduleId, Long userId) {
        attendanceResponseRepository.delete(getAnswer(scheduleId, userId));
    }

    /** 모집장이 스케줄의 특정 멤버 출석 상태를 처음 체크한다. 이미 체크된 기록이 있으면 거부한다. */
    @Transactional
    public AttendanceRecord checkAttendance(
            Long scheduleId, Long checkedBy, AttendanceCheckRequest request) {
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
        AttendanceRecord record = getRecord(scheduleId, request.getUserId());
        record.updateStatus(request.getStatus(), checkedBy);
        return record;
    }

    /** 출석 체크 기록을 삭제한다. */
    @Transactional
    public void deleteAttendance(Long scheduleId, Long userId) {
        attendanceRecordRepository.delete(getRecord(scheduleId, userId));
    }

    /** 모집장이 스케줄 하나의 출석 현황(상태별 인원 수 + 멤버별 내역)을 조회한다. */
    public AttendanceSummaryResponse getSummary(Long scheduleId) {
        List<AttendanceRecord> records = attendanceRecordRepository.findByScheduleId(scheduleId);
        return AttendanceSummaryResponse.of(scheduleId, records);
    }

    /** 사용자의 전체 스케줄 기준 누적 출석률(PRESENT 건수 / 전체 건수 * 100)을 계산한다. */
    public MyAttendanceRateResponse getMyAttendanceRate(Long userId) {
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

    /** (scheduleId, userId) 조합의 참석 여부 응답을 찾는다. 없으면 예외. */
    private AttendanceAnswer getAnswer(Long scheduleId, Long userId) {
        return attendanceResponseRepository
                .findByScheduleIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new EntityNotFoundException("참석 여부 응답을 찾을 수 없습니다."));
    }

    /** (scheduleId, userId) 조합의 출석 체크 기록을 찾는다. 없으면 예외. */
    private AttendanceRecord getRecord(Long scheduleId, Long userId) {
        return attendanceRecordRepository
                .findByScheduleIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new EntityNotFoundException("출석 기록을 찾을 수 없습니다."));
    }
}
