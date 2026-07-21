package com.mycom.myapp.member.controller;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.dto.request.MemberUpdateRequest;
import com.mycom.myapp.member.dto.response.MemberResponse;
import com.mycom.myapp.member.service.MemberService;
import com.mycom.myapp.member.service.ProfileImageStorageService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;
    private final ProfileImageStorageService profileImageStorageService;

    public MemberController(
            MemberService memberService, ProfileImageStorageService profileImageStorageService) {
        this.memberService = memberService;
        this.profileImageStorageService = profileImageStorageService;
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

    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MemberResponse> uploadProfileImage(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestParam("file") MultipartFile file) {
        String profileImageUrl = profileImageStorageService.store(file);
        return ApiResponse.success(
                memberService.updateProfileImage(
                        requireMemberId(authenticatedMember), profileImageUrl));
    }

    private Long requireMemberId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authenticatedMember.id();
    }
}
