package com.mycom.myapp.attendance.dto.request;

import com.mycom.myapp.attendance.entity.AttendanceResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 멤버가 스케줄 참석 여부를 응답/수정할 때 사용 (scheduleId는 경로 변수, userId는 인증된 사용자 id에서 채움) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAnswerRequest {

    private AttendanceResponse response;
}
