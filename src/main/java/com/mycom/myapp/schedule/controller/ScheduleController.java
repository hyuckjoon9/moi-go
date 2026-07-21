package com.mycom.myapp.schedule.controller;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.schedule.dto.request.ScheduleCreateRequest;
import com.mycom.myapp.schedule.dto.request.ScheduleQueryRequest;
import com.mycom.myapp.schedule.dto.request.ScheduleUpdateRequest;
import com.mycom.myapp.schedule.dto.response.SchedulePageResponse;
import com.mycom.myapp.schedule.dto.response.ScheduleResponse;
import com.mycom.myapp.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResponse>> create(
            @PathVariable("groupId") Long groupId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ScheduleCreateRequest request) {
        Long memberId = requireAuthenticatedMemberId(authenticatedMember);
        ScheduleResponse response = scheduleService.create(groupId, memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<SchedulePageResponse> getSchedules(
            @PathVariable("groupId") Long groupId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestParam(value = "scope", defaultValue = "upcoming") String scope,
            @RequestParam(value = "page", defaultValue = "0") String page,
            @RequestParam(value = "size", defaultValue = "20") String size) {
        Long memberId = requireAuthenticatedMemberId(authenticatedMember);
        ScheduleQueryRequest query = ScheduleQueryRequest.from(scope, page, size);
        return ApiResponse.success(
                scheduleService.getSchedules(
                        groupId, memberId, query.scope(), query.page(), query.size()));
    }

    @GetMapping("/{scheduleId}")
    public ApiResponse<ScheduleResponse> getSchedule(
            @PathVariable("groupId") Long groupId,
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        Long memberId = requireAuthenticatedMemberId(authenticatedMember);
        return ApiResponse.success(scheduleService.getSchedule(groupId, memberId, scheduleId));
    }

    @PutMapping("/{scheduleId}")
    public ApiResponse<ScheduleResponse> update(
            @PathVariable("groupId") Long groupId,
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ScheduleUpdateRequest request) {
        Long memberId = requireAuthenticatedMemberId(authenticatedMember);
        return ApiResponse.success(scheduleService.update(groupId, memberId, scheduleId, request));
    }

    private Long requireAuthenticatedMemberId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authenticatedMember.id();
    }
}
