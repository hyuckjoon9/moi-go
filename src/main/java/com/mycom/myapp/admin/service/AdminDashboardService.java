package com.mycom.myapp.admin.service;

import com.mycom.myapp.admin.dto.response.AdminDashboardResponse;
import com.mycom.myapp.admin.repository.AdminDashboardQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final AdminDashboardQueryRepository repository;

    public AdminDashboardService(AdminDashboardQueryRepository repository) {
        this.repository = repository;
    }

    public AdminDashboardResponse getDashboard() {
        return repository.findDashboard();
    }
}
