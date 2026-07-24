package com.mycom.myapp.admin.dto.request;

import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminRecruitmentVisibilityUpdateRequest(
        @NotNull RecruitmentVisibility expectedVisibility,
        @NotNull RecruitmentVisibility visibility,
        @NotBlank @Size(min = 5, max = 500) String reason) {

    public AdminRecruitmentVisibilityUpdateRequest {
        reason = reason == null ? null : reason.strip();
    }
}
