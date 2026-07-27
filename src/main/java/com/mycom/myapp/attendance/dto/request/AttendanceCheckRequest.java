package com.mycom.myapp.attendance.dto.request;

import com.mycom.myapp.attendance.entity.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCheckRequest {

    private Long userId;

    private AttendanceStatus status;
}
