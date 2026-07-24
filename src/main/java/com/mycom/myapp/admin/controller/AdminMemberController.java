package com.mycom.myapp.admin.controller;

import com.mycom.myapp.admin.dto.request.AdminMemberStatusUpdateRequest;
import com.mycom.myapp.admin.dto.response.AdminMemberDetailResponse;
import com.mycom.myapp.admin.dto.response.AdminMemberListResponse;
import com.mycom.myapp.admin.service.AdminMemberService;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.member.entity.MemberStatus;
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
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final AdminMemberService service;

    public AdminMemberController(AdminMemberService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AdminMemberListResponse> getMembers(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "role", required = false) MemberRole role,
            @RequestParam(name = "status", required = false) MemberStatus status,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.getMembers(keyword, role, status, page, size));
    }

    @GetMapping("/{memberId}")
    public ApiResponse<AdminMemberDetailResponse> getMember(
            @PathVariable(name = "memberId") Long memberId) {
        return ApiResponse.success(service.getMember(memberId));
    }

    @PatchMapping("/{memberId}/status")
    public ApiResponse<AdminMemberDetailResponse> changeStatus(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable(name = "memberId") Long memberId,
            @Valid @RequestBody AdminMemberStatusUpdateRequest request) {
        if (authenticatedMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(
                service.changeStatus(
                        authenticatedMember.id(),
                        memberId,
                        request.expectedStatus(),
                        request.status(),
                        request.reason()));
    }
}
