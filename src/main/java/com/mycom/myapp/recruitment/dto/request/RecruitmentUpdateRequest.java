package com.mycom.myapp.recruitment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record RecruitmentUpdateRequest(
        @NotBlank String title,
        @NotBlank String category,
        String description,
        String goal,
        String method,
        @NotBlank String meetingType,
        String location,
        String onlineLink,
        String meetingDay,
        @NotNull @Positive Integer capacity,
        @NotNull LocalDate recruitmentDeadline,
        String expectedDuration,
        String conditions) {}
