package com.mycom.myapp.recruitment.controller;

import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.recruitment.dto.request.RecruitmentCreateRequest;
import com.mycom.myapp.recruitment.dto.response.RecruitmentResponse;
import com.mycom.myapp.recruitment.service.RecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}