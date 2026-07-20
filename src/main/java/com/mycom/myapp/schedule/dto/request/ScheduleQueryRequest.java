package com.mycom.myapp.schedule.dto.request;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;

public record ScheduleQueryRequest(ScheduleScope scope, int page, int size) {

    public static ScheduleQueryRequest from(String scope, String page, String size) {
        int parsedPage;
        int parsedSize;
        try {
            parsedPage = Integer.parseInt(page);
            parsedSize = Integer.parseInt(size);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (parsedPage < 0 || parsedSize < 1 || parsedSize > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new ScheduleQueryRequest(ScheduleScope.from(scope), parsedPage, parsedSize);
    }
}
