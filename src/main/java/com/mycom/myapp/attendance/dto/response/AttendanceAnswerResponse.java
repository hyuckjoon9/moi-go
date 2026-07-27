package com.mycom.myapp.attendance.dto.response;

import com.mycom.myapp.attendance.entity.AttendanceAnswer;
import com.mycom.myapp.attendance.entity.AttendanceResponse;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AttendanceAnswerResponse {

    private Long id;
    private Long scheduleId;
    private Long userId;
    private AttendanceResponse response;
    private LocalDateTime respondedAt;

    public static AttendanceAnswerResponse of(AttendanceAnswer answer) {
        return AttendanceAnswerResponse.builder()
                .id(answer.getId())
                .scheduleId(answer.getScheduleId())
                .userId(answer.getUserId())
                .response(answer.getResponse())
                .respondedAt(answer.getRespondedAt())
                .build();
    }
}
