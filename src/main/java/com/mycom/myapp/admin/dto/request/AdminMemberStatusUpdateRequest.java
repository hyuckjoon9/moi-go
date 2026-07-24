package com.mycom.myapp.admin.dto.request;

import com.mycom.myapp.member.entity.MemberStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminMemberStatusUpdateRequest(
        @NotNull MemberStatus expectedStatus,
        @NotNull MemberStatus status,
        @NotBlank @Size(min = 5, max = 500) String reason) {

    public AdminMemberStatusUpdateRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }
}
