package com.mycom.myapp.recruitment.dto.request;

import java.time.LocalDate;

public record RecruitmentCreateRequest(
        String title,
        String category,
        String description,
        String goal,
        String method,
        String meetingType,
        String location,
        String onlineLink,
        String meetingDay,
        Integer capacity,
        LocalDate recruitmentDeadline,
        String expectedDuration,
        String conditions) {}
