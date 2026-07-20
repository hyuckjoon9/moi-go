package com.mycom.myapp.recruitment.controller;

import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.recruitment.dto.request.RecruitmentCreateRequest;
import com.mycom.myapp.recruitment.dto.response.RecruitmentResponse;
import com.mycom.myapp.recruitment.service.RecruitmentService;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<RecruitmentResponse> create(@RequestBody RecruitmentCreateRequest request) {
        return ApiResponse.success(recruitmentService.create(request));
    }
}
