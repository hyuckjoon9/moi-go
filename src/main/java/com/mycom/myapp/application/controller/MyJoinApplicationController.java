package com.mycom.myapp.application.controller;

import com.mycom.myapp.application.dto.response.MyJoinApplicationResponse;
import com.mycom.myapp.application.entity.ApplicationStatus;
import com.mycom.myapp.application.service.JoinApplicationService;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/join-applications")
public class MyJoinApplicationController {

    private final JoinApplicationService joinApplicationService;

    @GetMapping("/me")
    public ApiResponse<List<MyJoinApplicationResponse>> getMyApplications(
            @AuthenticationPrincipal AuthenticatedMember principal,
            @RequestParam(name = "status", required = false) ApplicationStatus status) {
        return ApiResponse.success(
                joinApplicationService.getMyApplications(principal.id(), status));
    }
}
