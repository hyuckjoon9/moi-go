package com.mycom.myapp.recruitment.controller;

import com.mycom.myapp.application.service.JoinApplicationService;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.recruitment.dto.request.RecruitmentCreateRequest;
import com.mycom.myapp.recruitment.dto.request.RecruitmentUpdateRequest;
import com.mycom.myapp.recruitment.dto.response.RecruitmentResponse;
import com.mycom.myapp.recruitment.service.RecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recruitment-posts")
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    @PostMapping
    public ApiResponse<RecruitmentResponse> create(
            @AuthenticationPrincipal AuthenticatedMember principal,
            @Valid @RequestBody RecruitmentCreateRequest request) {
        return ApiResponse.success(recruitmentService.create(principal.id(), request));
    }

    @GetMapping
    public ApiResponse<Page<RecruitmentResponse>> getList(
            @RequestParam(name = "category", required = false) String category,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.success(recruitmentService.getList(category, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<RecruitmentResponse> getDetail(@PathVariable("id") Long id) {
        return ApiResponse.success(recruitmentService.getDetail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<RecruitmentResponse> update(
            @AuthenticationPrincipal AuthenticatedMember principal,
            @PathVariable("id") Long id,
            @Valid @RequestBody RecruitmentUpdateRequest request) {
        return ApiResponse.success(recruitmentService.update(id, principal.id(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedMember principal, @PathVariable("id") Long id) {
        recruitmentService.delete(id, principal.id());
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/close")
    public ApiResponse<RecruitmentResponse> close(
            @AuthenticationPrincipal AuthenticatedMember principal, @PathVariable("id") Long id) {
        return ApiResponse.success(recruitmentService.close(id, principal.id()));
    }

    @PatchMapping("/{id}/end")
    public ApiResponse<RecruitmentResponse> end(
            @AuthenticationPrincipal AuthenticatedMember principal, @PathVariable("id") Long id) {
        return ApiResponse.success(recruitmentService.end(id, principal.id()));
    }
}
