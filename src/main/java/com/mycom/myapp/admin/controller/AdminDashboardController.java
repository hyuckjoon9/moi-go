package com.mycom.myapp.admin.controller;

import com.mycom.myapp.admin.dto.response.AdminDashboardResponse;
import com.mycom.myapp.admin.service.AdminDashboardService;
import com.mycom.myapp.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AdminDashboardResponse> getDashboard() {
        return ApiResponse.success(service.getDashboard());
    }
}
