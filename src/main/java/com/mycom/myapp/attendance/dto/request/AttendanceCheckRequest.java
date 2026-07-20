package com.mycom.myapp.attendance.dto.request;

import com.mycom.myapp.attendance.entity.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 모집장이 스케줄의 특정 멤버 출석 상태를 체크/수정할 때 사용 (scheduleId는 경로 변수, checkedBy는 인증된 모집장 id에서 채움) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCheckRequest {

    private Long userId;

    private AttendanceStatus status;
}
