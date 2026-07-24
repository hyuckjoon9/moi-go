package com.mycom.myapp.admin.dto.response;

import java.time.LocalDateTime;

public final class AdminOperationsResponse {

    private AdminOperationsResponse() {}

    public record GroupItem(
            long groupId,
            long postId,
            String name,
            String status,
            long activeMemberCount,
            LocalDateTime createdAt) {}

    public record ScheduleItem(
            long scheduleId,
            long groupId,
            String groupName,
            long creatorId,
            String title,
            LocalDateTime scheduledAt,
            LocalDateTime responseDeadline) {}

    public record AttendanceItem(
            long attendanceRecordId,
            long scheduleId,
            String scheduleTitle,
            long groupId,
            String groupName,
            long memberId,
            String memberNickname,
            String status,
            Long checkedBy,
            LocalDateTime checkedAt) {}

    public record ActivityItem(
            long activityRecordId,
            long scheduleId,
            String scheduleTitle,
            long groupId,
            String groupName,
            long authorId,
            String topic,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record AuditLogItem(
            long auditLogId,
            long adminId,
            String action,
            String targetType,
            long targetId,
            String targetLabel,
            String beforeValue,
            String afterValue,
            String reason,
            LocalDateTime createdAt) {}
}
