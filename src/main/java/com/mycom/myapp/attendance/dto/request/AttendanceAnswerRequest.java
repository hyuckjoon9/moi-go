package com.mycom.myapp.attendance.dto.request;

import com.mycom.myapp.attendance.entity.AttendanceResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAnswerRequest {

    private AttendanceResponse response;
}
