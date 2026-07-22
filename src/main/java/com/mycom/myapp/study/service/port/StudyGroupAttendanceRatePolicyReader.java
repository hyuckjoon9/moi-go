package com.mycom.myapp.study.service.port;

import com.mycom.myapp.study.service.StudyGroupAttendanceRatePolicy;

public interface StudyGroupAttendanceRatePolicyReader {

    StudyGroupAttendanceRatePolicy getAttendanceRatePolicy(Long groupId, Long requesterId);
}
