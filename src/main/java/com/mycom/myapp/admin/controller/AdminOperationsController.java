package com.mycom.myapp.admin.controller;

import com.mycom.myapp.admin.dto.response.AdminOperationsResponse;
import com.mycom.myapp.admin.dto.response.AdminPageResponse;
import com.mycom.myapp.admin.service.AdminOperationsService;
import com.mycom.myapp.global.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminOperationsController {
    private final AdminOperationsService service;

    public AdminOperationsController(AdminOperationsService service) {
        this.service = service;
    }

    @GetMapping("/groups")
    public ApiResponse<AdminPageResponse<AdminOperationsResponse.GroupItem>> getGroups(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.getGroups(keyword, status, page, size));
    }

    @GetMapping("/schedules")
    public ApiResponse<AdminPageResponse<AdminOperationsResponse.ScheduleItem>> getSchedules(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.getSchedules(keyword, page, size));
    }

    @GetMapping("/attendance-records")
    public ApiResponse<AdminPageResponse<AdminOperationsResponse.AttendanceItem>>
            getAttendanceRecords(
                    @RequestParam(required = false) String keyword,
                    @RequestParam(required = false) String status,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.getAttendanceRecords(keyword, status, page, size));
    }

    @GetMapping("/activity-records")
    public ApiResponse<AdminPageResponse<AdminOperationsResponse.ActivityItem>> getActivityRecords(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.getActivityRecords(keyword, page, size));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<AdminPageResponse<AdminOperationsResponse.AuditLogItem>> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.getAuditLogs(action, targetType, keyword, page, size));
    }
}
