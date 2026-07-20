package com.mycom.myapp.application.controller;

import com.mycom.myapp.application.dto.request.JoinApplicationCreateRequest;
import com.mycom.myapp.application.dto.response.JoinApplicationResponse;
import com.mycom.myapp.application.service.JoinApplicationService;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recruitment-posts/{postId}/applications")
public class JoinApplicationController {

    private final JoinApplicationService joinApplicationService;

    @PostMapping
    public ApiResponse<JoinApplicationResponse> create(
            @AuthenticationPrincipal AuthenticatedMember principal,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody JoinApplicationCreateRequest request) {
        return ApiResponse.success(joinApplicationService.create(postId, principal.id(), request));
    }
}
