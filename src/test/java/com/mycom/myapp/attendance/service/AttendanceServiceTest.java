package com.mycom.myapp.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mycom.myapp.attendance.dto.request.AttendanceAnswerRequest;
import com.mycom.myapp.attendance.dto.request.AttendanceCheckRequest;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private AttendanceResponseRepository attendanceResponseRepository;

    @InjectMocks private AttendanceService attendanceService;

    @Test
    void submitAnswerSavesNewAnswer() {
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
    void changeAnswerUpdatesExistingAnswer() {
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
    void deleteAnswerDeletesExistingAnswer() {
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
    void checkAttendanceSavesNewRecord() {
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
    void updateAttendanceUpdatesExistingRecord() {
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
    void deleteAttendanceDeletesExistingRecord() {
        AttendanceRecord existing =
                AttendanceRecord.builder()
                        .scheduleId(10L)
                        .userId(20L)
                        .status(AttendanceStatus.PRESENT)
                        .checkedBy(1L)
                        .build();
        given(attendanceRecordRepository.findByScheduleIdAndUserId(10L, 20L))
                .willReturn(Optional.of(existing));

        attendanceService.deleteAttendance(10L, 20L);

        verify(attendanceRecordRepository).delete(existing);
    }

    @Test
    void getSummaryAggregatesRecordsByStatus() {
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

        AttendanceSummaryResponse summary = attendanceService.getSummary(10L);

        assertThat(summary.getTotalCount()).isEqualTo(3);
        assertThat(summary.getPresentCount()).isEqualTo(1);
        assertThat(summary.getLateCount()).isEqualTo(1);
        assertThat(summary.getAbsentCount()).isEqualTo(1);
        assertThat(summary.getExcusedCount()).isEqualTo(0);
        assertThat(summary.getMembers()).hasSize(3);
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

        MyAttendanceRateResponse response = attendanceService.getMyAttendanceRate(20L);

        assertThat(response.getTotalCount()).isEqualTo(5);
        assertThat(response.getAttendanceRate()).isEqualTo(60.0);
    }

    @Test
    void getMyAttendanceRateReturnsZeroWhenNoRecords() {
        given(attendanceRecordRepository.countByUserIdAndStatus(any(), any())).willReturn(0L);

        MyAttendanceRateResponse response = attendanceService.getMyAttendanceRate(20L);

        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getAttendanceRate()).isEqualTo(0.0);
    }
}
