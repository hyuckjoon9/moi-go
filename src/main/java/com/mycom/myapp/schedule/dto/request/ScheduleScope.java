package com.mycom.myapp.schedule.dto.request;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;

public enum ScheduleScope {
    UPCOMING,
    PAST;

    public static ScheduleScope from(String value) {
        if ("upcoming".equals(value)) {
            return UPCOMING;
        }
        if ("past".equals(value)) {
            return PAST;
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
}
