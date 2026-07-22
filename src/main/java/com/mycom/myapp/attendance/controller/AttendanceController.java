package com.mycom.myapp.attendance.controller;

import com.mycom.myapp.attendance.dto.request.AttendanceAnswerRequest;
import com.mycom.myapp.attendance.dto.request.AttendanceCheckRequest;
import com.mycom.myapp.attendance.dto.response.AttendanceAnswerResponse;
import com.mycom.myapp.attendance.dto.response.AttendanceRecordResponse;
import com.mycom.myapp.attendance.dto.response.AttendanceSummaryResponse;
import com.mycom.myapp.attendance.dto.response.MyAttendanceRateResponse;
import com.mycom.myapp.attendance.service.AttendanceService;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.security.AuthenticatedMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    // TODO: checkedBy가 해당 스케줄 그룹의 룰을 받아올 때 출석률 조회도 수정 (권한이 있는 사람만 가능하도록?)

    /** 참석 여부 응답 등록 */
    @PostMapping("/schedules/{scheduleId}/answers")
    public ResponseEntity<AttendanceAnswerResponse> submitAnswer(
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody AttendanceAnswerRequest request) {
        Long userId = requireAuthenticatedId(authenticatedMember);
        AttendanceAnswerResponse response =
                AttendanceAnswerResponse.of(
                        attendanceService.submitAnswer(scheduleId, userId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 참석 여부 응답 수정 */
    @PutMapping("/schedules/{scheduleId}/answers")
    public ResponseEntity<AttendanceAnswerResponse> changeAnswer(
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody AttendanceAnswerRequest request) {
        Long userId = requireAuthenticatedId(authenticatedMember);
        AttendanceAnswerResponse response =
                AttendanceAnswerResponse.of(
                        attendanceService.changeAnswer(scheduleId, userId, request));
        return ResponseEntity.ok(response);
    }

    /** 참석 여부 응답 삭제 */
    @DeleteMapping("/schedules/{scheduleId}/answers")
    public ResponseEntity<Void> deleteAnswer(
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        Long userId = requireAuthenticatedId(authenticatedMember);
        attendanceService.deleteAnswer(scheduleId, userId);
        return ResponseEntity.noContent().build();
    }

    /** 출석 체크 등록 (모집장) */
    @PostMapping("/schedules/{scheduleId}/records")
    public ResponseEntity<AttendanceRecordResponse> checkAttendance(
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody AttendanceCheckRequest request) {
        Long checkedBy = requireAuthenticatedId(authenticatedMember);
        AttendanceRecordResponse response =
                AttendanceRecordResponse.of(
                        attendanceService.checkAttendance(scheduleId, checkedBy, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 출석 체크 수정 (모집장) */
    @PutMapping("/schedules/{scheduleId}/records")
    public ResponseEntity<AttendanceRecordResponse> updateAttendance(
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody AttendanceCheckRequest request) {
        Long checkedBy = requireAuthenticatedId(authenticatedMember);
        AttendanceRecordResponse response =
                AttendanceRecordResponse.of(
                        attendanceService.updateAttendance(scheduleId, checkedBy, request));
        return ResponseEntity.ok(response);
    }

    /** 출석 기록 삭제 */
    @DeleteMapping("/schedules/{scheduleId}/records/{userId}")
    public ResponseEntity<Void> deleteAttendance(
            @PathVariable("scheduleId") Long scheduleId, @PathVariable("userId") Long userId) {
        attendanceService.deleteAttendance(scheduleId, userId);
        return ResponseEntity.noContent().build();
    }

    /** 스케줄 출석 현황 요약 조회 (모집장) */
    @GetMapping("/schedules/{scheduleId}/records/summary")
    public ResponseEntity<AttendanceSummaryResponse> getSummary(
            @PathVariable("scheduleId") Long scheduleId) {
        return ResponseEntity.ok(attendanceService.getSummary(scheduleId));
    }

    /** 개인 누적 출석률 조회 */
    @GetMapping("/users/{userId}/rate")
    public ResponseEntity<MyAttendanceRateResponse> getMyAttendanceRate(
            @PathVariable("userId") Long userId) {
        return ResponseEntity.ok(attendanceService.getMyAttendanceRate(userId));
    }

    private Long requireAuthenticatedId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authenticatedMember.id();
    }
}
