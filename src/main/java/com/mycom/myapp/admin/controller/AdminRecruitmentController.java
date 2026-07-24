package com.mycom.myapp.admin.controller;

import com.mycom.myapp.admin.dto.request.AdminRecruitmentVisibilityUpdateRequest;
import com.mycom.myapp.admin.dto.response.AdminRecruitmentDetailResponse;
import com.mycom.myapp.admin.dto.response.AdminRecruitmentListResponse;
import com.mycom.myapp.admin.service.AdminRecruitmentService;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/recruitments")
public class AdminRecruitmentController {

    private final AdminRecruitmentService service;

    public AdminRecruitmentController(AdminRecruitmentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AdminRecruitmentListResponse> getRecruitments(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) RecruitmentStatus status,
            @RequestParam(name = "visibility", required = false) RecruitmentVisibility visibility,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                service.getRecruitments(keyword, status, visibility, page, size));
    }

    @GetMapping("/{recruitmentId}")
    public ApiResponse<AdminRecruitmentDetailResponse> getRecruitment(
            @PathVariable(name = "recruitmentId") Long recruitmentId) {
        return ApiResponse.success(service.getRecruitment(recruitmentId));
    }

    @PatchMapping("/{recruitmentId}/visibility")
    public ApiResponse<AdminRecruitmentDetailResponse> changeVisibility(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable(name = "recruitmentId") Long recruitmentId,
            @Valid @RequestBody AdminRecruitmentVisibilityUpdateRequest request) {
        if (authenticatedMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(
                service.changeVisibility(
                        authenticatedMember.id(),
                        recruitmentId,
                        request.expectedVisibility(),
                        request.visibility(),
                        request.reason()));
    }
}
