package com.mycom.myapp.auth.controller;

import com.mycom.myapp.auth.dto.request.LoginRequest;
import com.mycom.myapp.auth.dto.request.ReissueRequest;
import com.mycom.myapp.auth.dto.response.TokenResponse;
import com.mycom.myapp.auth.service.AuthService;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.member.dto.request.MemberCreateRequest;
import com.mycom.myapp.member.dto.response.MemberResponse;
import com.mycom.myapp.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final MemberService memberService;

    public AuthController(AuthService authService, MemberService memberService) {
        this.authService = authService;
        this.memberService = memberService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(
            @Valid @RequestBody MemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memberService.create(request)));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ApiResponse.success(authService.reissue(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody ReissueRequest request) {
        authService.logout(request);
        return ApiResponse.ok();
    }
}
