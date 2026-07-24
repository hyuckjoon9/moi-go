package com.mycom.myapp.schedule.dto.request;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDateTime;

public class ScheduleDeadlineUpdateRequest {

    private LocalDateTime responseDeadline;
    private boolean responseDeadlineProvided;

    public ScheduleDeadlineUpdateRequest() {}

    public void setResponseDeadline(LocalDateTime responseDeadline) {
        this.responseDeadline = responseDeadline;
        responseDeadlineProvided = true;
    }

    public LocalDateTime responseDeadline() {
        return responseDeadline;
    }

    @AssertTrue(message = "responseDeadline 필드는 필수입니다.")
    public boolean isResponseDeadlineProvided() {
        return responseDeadlineProvided;
    }
}
