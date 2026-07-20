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
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceResponseRepository attendanceResponseRepository;

    @Transactional
    public AttendanceAnswer submitAnswer(
            Long scheduleId, Long userId, AttendanceAnswerRequest request) {
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
        AttendanceAnswer answer = getAnswer(scheduleId, userId);
        answer.changeResponse(request.getResponse());
        return answer;
    }

    @Transactional
    public void deleteAnswer(Long scheduleId, Long userId) {
        attendanceResponseRepository.delete(getAnswer(scheduleId, userId));
    }

    @Transactional
    public AttendanceRecord checkAttendance(
            Long scheduleId, Long checkedBy, AttendanceCheckRequest request) {
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
        AttendanceRecord record = getRecord(scheduleId, request.getUserId());
        record.updateStatus(request.getStatus(), checkedBy);
        return record;
    }

    @Transactional
    public void deleteAttendance(Long scheduleId, Long userId) {
        attendanceRecordRepository.delete(getRecord(scheduleId, userId));
    }

    public AttendanceSummaryResponse getSummary(Long scheduleId) {
        List<AttendanceRecord> records = attendanceRecordRepository.findByScheduleId(scheduleId);
        return AttendanceSummaryResponse.of(scheduleId, records);
    }

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

    private AttendanceAnswer getAnswer(Long scheduleId, Long userId) {
        return attendanceResponseRepository
                .findByScheduleIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new EntityNotFoundException("참석 여부 응답을 찾을 수 없습니다."));
    }

    private AttendanceRecord getRecord(Long scheduleId, Long userId) {
        return attendanceRecordRepository
                .findByScheduleIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new EntityNotFoundException("출석 기록을 찾을 수 없습니다."));
    }
}
