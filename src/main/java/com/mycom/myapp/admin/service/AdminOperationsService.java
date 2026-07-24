package com.mycom.myapp.admin.service;

import com.mycom.myapp.admin.dto.response.AdminOperationsResponse;
import com.mycom.myapp.admin.dto.response.AdminPageResponse;
import com.mycom.myapp.admin.repository.AdminOperationsQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminOperationsService {
    private final AdminOperationsQueryRepository repository;

    public AdminOperationsService(AdminOperationsQueryRepository repository) {
        this.repository = repository;
    }

    public AdminPageResponse<AdminOperationsResponse.GroupItem> getGroups(
            String keyword, String status, int page, int size) {
        return repository.findGroups(keyword, status, page, size);
    }

    public AdminPageResponse<AdminOperationsResponse.ScheduleItem> getSchedules(
            String keyword, int page, int size) {
        return repository.findSchedules(keyword, page, size);
    }

    public AdminPageResponse<AdminOperationsResponse.AttendanceItem> getAttendanceRecords(
            String keyword, String status, int page, int size) {
        return repository.findAttendanceRecords(keyword, status, page, size);
    }

    public AdminPageResponse<AdminOperationsResponse.ActivityItem> getActivityRecords(
            String keyword, int page, int size) {
        return repository.findActivityRecords(keyword, page, size);
    }

    public AdminPageResponse<AdminOperationsResponse.AuditLogItem> getAuditLogs(
            String action, String targetType, String keyword, int page, int size) {
        return repository.findAuditLogs(action, targetType, keyword, page, size);
    }
}
