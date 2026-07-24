package com.mycom.myapp.admin.dto.response;

import com.mycom.myapp.admin.entity.AdminAction;
import com.mycom.myapp.admin.entity.AdminTargetType;
import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        MemberCounts members,
        RecruitmentCounts recruitments,
        GroupCounts groups,
        List<RecentAction> recentActions) {

    public record MemberCounts(long total, long active, long suspended, long withdrawn) {}

    public record RecruitmentCounts(long recruiting, long closed, long active, long ended) {}

    public record GroupCounts(long active, long ended) {}

    public record RecentAction(
            Long auditLogId,
            AdminAction action,
            AdminTargetType targetType,
            Long targetId,
            String targetLabel,
            Long adminId,
            String reason,
            LocalDateTime createdAt) {}
}
