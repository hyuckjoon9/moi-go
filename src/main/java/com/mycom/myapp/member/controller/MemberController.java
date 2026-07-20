package com.mycom.myapp.member.controller;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.dto.request.MemberUpdateRequest;
import com.mycom.myapp.member.dto.response.MemberResponse;
import com.mycom.myapp.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    public ApiResponse<MemberResponse> getMe(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        return ApiResponse.success(memberService.getMe(requireMemberId(authenticatedMember)));
    }

    @PatchMapping("/me")
    public ApiResponse<MemberResponse> updateMe(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody MemberUpdateRequest request) {
        return ApiResponse.success(
                memberService.updateMe(requireMemberId(authenticatedMember), request));
    }

    private Long requireMemberId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authenticatedMember.id();
    }
}
