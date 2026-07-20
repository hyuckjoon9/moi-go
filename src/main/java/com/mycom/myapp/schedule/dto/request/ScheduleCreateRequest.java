package com.mycom.myapp.schedule.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ScheduleCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull LocalDateTime scheduledAt,
        @Size(max = 255) String location,
        @Size(max = 500) String onlineLink,
        @Size(max = 5000) String content,
        @Size(max = 5000) String materials,
        LocalDateTime responseDeadline) {

    public ScheduleCreateRequest {
        title = normalize(title);
        location = normalize(location);
        onlineLink = normalize(onlineLink);
        content = normalize(content);
        materials = normalize(materials);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
