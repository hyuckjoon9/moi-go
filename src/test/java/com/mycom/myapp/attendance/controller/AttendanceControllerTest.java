package com.mycom.myapp.attendance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.attendance.dto.request.AttendanceAnswerRequest;
import com.mycom.myapp.attendance.dto.request.AttendanceCheckRequest;
import com.mycom.myapp.attendance.dto.response.AttendanceAnswerResponse;
import com.mycom.myapp.attendance.dto.response.AttendanceRecordResponse;
import com.mycom.myapp.attendance.dto.response.AttendanceSummaryResponse;
import com.mycom.myapp.attendance.dto.response.MyAttendanceRateResponse;
import com.mycom.myapp.attendance.entity.AttendanceAnswer;
import com.mycom.myapp.attendance.entity.AttendanceRecord;
import com.mycom.myapp.attendance.entity.AttendanceResponse;
import com.mycom.myapp.attendance.entity.AttendanceStatus;
import com.mycom.myapp.attendance.service.AttendanceService;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.entity.MemberRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AttendanceControllerTest {

    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private final AttendanceController controller = new AttendanceController(attendanceService);
    private final AuthenticatedMember authenticatedMember =
            new AuthenticatedMember(2L, "member@example.com", MemberRole.USER);

    @Test
    void submitAnswerReturnsCreatedWithAuthenticatedUserId() {
        AttendanceAnswerRequest request = new AttendanceAnswerRequest(AttendanceResponse.ATTEND);
        AttendanceAnswer answer =
                AttendanceAnswer.builder()
                        .scheduleId(10L)
                        .userId(2L)
                        .response(AttendanceResponse.ATTEND)
                        .build();
        when(attendanceService.submitAnswer(10L, 2L, request)).thenReturn(answer);

        ResponseEntity<AttendanceAnswerResponse> response =
                controller.submitAnswer(10L, authenticatedMember, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getUserId()).isEqualTo(2L);
        assertThat(response.getBody().getResponse()).isEqualTo(AttendanceResponse.ATTEND);
        verify(attendanceService).submitAnswer(10L, 2L, request);
    }

    @Test
    void submitAnswerRejectsMissingAuthenticatedMember() {
        AttendanceAnswerRequest request = new AttendanceAnswerRequest(AttendanceResponse.ATTEND);

        assertThatThrownBy(() -> controller.submitAnswer(10L, null, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void changeAnswerReturnsOkWithAuthenticatedUserId() {
        AttendanceAnswerRequest request = new AttendanceAnswerRequest(AttendanceResponse.ABSENT);
        AttendanceAnswer answer =
                AttendanceAnswer.builder()
                        .scheduleId(10L)
                        .userId(2L)
                        .response(AttendanceResponse.ABSENT)
                        .build();
        when(attendanceService.changeAnswer(10L, 2L, request)).thenReturn(answer);

        ResponseEntity<AttendanceAnswerResponse> response =
                controller.changeAnswer(10L, authenticatedMember, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResponse()).isEqualTo(AttendanceResponse.ABSENT);
        verify(attendanceService).changeAnswer(10L, 2L, request);
    }

    @Test
    void changeAnswerRejectsMissingAuthenticatedMember() {
        AttendanceAnswerRequest request = new AttendanceAnswerRequest(AttendanceResponse.ABSENT);

        assertThatThrownBy(() -> controller.changeAnswer(10L, null, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void deleteAnswerDeletesForAuthenticatedUserId() {
        ResponseEntity<Void> response = controller.deleteAnswer(10L, authenticatedMember);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(attendanceService).deleteAnswer(10L, 2L);
    }

    @Test
    void deleteAnswerRejectsMissingAuthenticatedMember() {
        assertThatThrownBy(() -> controller.deleteAnswer(10L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void checkAttendanceReturnsCreatedWithAuthenticatedCheckedBy() {
        AttendanceCheckRequest request = new AttendanceCheckRequest(5L, AttendanceStatus.PRESENT);
        AttendanceRecord record =
                AttendanceRecord.builder()
                        .scheduleId(10L)
                        .userId(5L)
                        .status(AttendanceStatus.PRESENT)
                        .checkedBy(2L)
                        .build();
        when(attendanceService.checkAttendance(10L, 2L, request)).thenReturn(record);

        ResponseEntity<AttendanceRecordResponse> response =
                controller.checkAttendance(10L, authenticatedMember, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getCheckedBy()).isEqualTo(2L);
        assertThat(response.getBody().getUserId()).isEqualTo(5L);
        verify(attendanceService).checkAttendance(10L, 2L, request);
    }

    @Test
    void checkAttendanceRejectsMissingAuthenticatedMember() {
        AttendanceCheckRequest request = new AttendanceCheckRequest(5L, AttendanceStatus.PRESENT);

        assertThatThrownBy(() -> controller.checkAttendance(10L, null, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void updateAttendanceReturnsOkWithAuthenticatedCheckedBy() {
        AttendanceCheckRequest request = new AttendanceCheckRequest(5L, AttendanceStatus.LATE);
        AttendanceRecord record =
                AttendanceRecord.builder()
                        .scheduleId(10L)
                        .userId(5L)
                        .status(AttendanceStatus.LATE)
                        .checkedBy(2L)
                        .build();
        when(attendanceService.updateAttendance(10L, 2L, request)).thenReturn(record);

        ResponseEntity<AttendanceRecordResponse> response =
                controller.updateAttendance(10L, authenticatedMember, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(AttendanceStatus.LATE);
        verify(attendanceService).updateAttendance(10L, 2L, request);
    }

    @Test
    void updateAttendanceRejectsMissingAuthenticatedMember() {
        AttendanceCheckRequest request = new AttendanceCheckRequest(5L, AttendanceStatus.LATE);

        assertThatThrownBy(() -> controller.updateAttendance(10L, null, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void deleteAttendanceDeletesForAuthenticatedRequester() {
        ResponseEntity<Void> response = controller.deleteAttendance(10L, 5L, authenticatedMember);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(attendanceService).deleteAttendance(10L, 5L, 2L);
    }

    @Test
    void deleteAttendanceRejectsMissingAuthenticatedMember() {
        assertThatThrownBy(() -> controller.deleteAttendance(10L, 5L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void getSummaryReturnsServiceResult() {
        AttendanceSummaryResponse summary =
                AttendanceSummaryResponse.builder()
                        .scheduleId(10L)
                        .totalCount(1)
                        .presentCount(1)
                        .lateCount(0)
                        .absentCount(0)
                        .excusedCount(0)
                        .members(List.of())
                        .build();
        when(attendanceService.getSummary(10L, 2L)).thenReturn(summary);

        ResponseEntity<AttendanceSummaryResponse> response =
                controller.getSummary(10L, authenticatedMember);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(summary);
    }

    @Test
    void getSummaryRejectsMissingAuthenticatedMember() {
        assertThatThrownBy(() -> controller.getSummary(10L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void getMyAttendanceRateReturnsServiceResult() {
        AuthenticatedMember self = new AuthenticatedMember(5L, "self@example.com", MemberRole.USER);
        MyAttendanceRateResponse rate = MyAttendanceRateResponse.of(5L, 3L, 1L, 0L, 0L);
        when(attendanceService.getMyAttendanceRate(5L, 5L)).thenReturn(rate);

        ResponseEntity<MyAttendanceRateResponse> response =
                controller.getMyAttendanceRate(5L, self);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(rate);
    }

    @Test
    void getMyAttendanceRateRejectsMissingAuthenticatedMember() {
        assertThatThrownBy(() -> controller.getMyAttendanceRate(5L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void getGroupAttendanceRatesReturnsServiceResult() {
        List<MyAttendanceRateResponse> rates =
                List.of(MyAttendanceRateResponse.of(20L, 3L, 1L, 0L, 0L));
        when(attendanceService.getGroupAttendanceRates(100L, 2L)).thenReturn(rates);

        ResponseEntity<List<MyAttendanceRateResponse>> response =
                controller.getGroupAttendanceRates(100L, authenticatedMember);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(rates);
    }

    @Test
    void getGroupAttendanceRatesRejectsMissingAuthenticatedMember() {
        assertThatThrownBy(() -> controller.getGroupAttendanceRates(100L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
