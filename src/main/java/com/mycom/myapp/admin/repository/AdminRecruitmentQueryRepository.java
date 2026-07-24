package com.mycom.myapp.admin.repository;

import com.mycom.myapp.admin.dto.response.AdminRecruitmentDetailResponse;
import com.mycom.myapp.admin.dto.response.AdminRecruitmentListResponse;
import com.mycom.myapp.admin.entity.AdminAction;
import com.mycom.myapp.admin.entity.AdminTargetType;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRecruitmentQueryRepository {

    private final JdbcClient jdbcClient;

    public AdminRecruitmentQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public AdminRecruitmentListResponse findRecruitments(
            String keyword,
            RecruitmentStatus status,
            RecruitmentVisibility visibility,
            int page,
            int size) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (lower(r.title) like :keyword or lower(u.nickname) like :keyword)");
        }
        if (status != null) {
            where.append(" and r.status = :status");
        }
        if (visibility != null) {
            where.append(" and r.visibility = :visibility");
        }

        long totalElements =
                bind(
                                jdbcClient.sql(
                                        "select count(*) from recruitment_posts r join users u on u.id = r.leader_id"
                                                + where),
                                keyword,
                                status,
                                visibility)
                        .query(Long.class)
                        .single();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        List<AdminRecruitmentListResponse.Item> items =
                bind(
                                jdbcClient.sql(
                                        """
                                        select r.id, r.leader_id, u.nickname as leader_nickname, r.title, r.category,
                                               r.status, r.visibility, r.created_at
                                        from recruitment_posts r join users u on u.id = r.leader_id
                                        """
                                                + where
                                                + " order by r.id desc limit :size offset :offset"),
                                keyword,
                                status,
                                visibility)
                        .param("size", size)
                        .param("offset", page * size)
                        .query(
                                (row, rowNum) ->
                                        new AdminRecruitmentListResponse.Item(
                                                row.getLong("id"),
                                                row.getLong("leader_id"),
                                                row.getString("leader_nickname"),
                                                row.getString("title"),
                                                row.getString("category"),
                                                RecruitmentStatus.valueOf(row.getString("status")),
                                                RecruitmentVisibility.valueOf(
                                                        row.getString("visibility")),
                                                row.getTimestamp("created_at").toLocalDateTime()))
                        .list();
        return new AdminRecruitmentListResponse(items, page, size, totalElements, totalPages);
    }

    public AdminRecruitmentDetailResponse findRecruitment(Long recruitmentId) {
        AdminRecruitmentDetailResponse detail =
                jdbcClient
                        .sql(
                                """
                        select r.id, r.leader_id, r.title, r.category, r.description, r.goal, r.method,
                               r.meeting_type, r.location, r.online_link, r.meeting_day, r.capacity,
                               r.recruitment_deadline, r.expected_duration, r.conditions, r.status,
                               r.visibility, g.id as group_id, r.created_at, r.updated_at
                        from recruitment_posts r
                        left join study_groups g on g.post_id = r.id
                        where r.id = :recruitmentId
                        """)
                        .param("recruitmentId", recruitmentId)
                        .query(
                                (row, rowNum) ->
                                        new AdminRecruitmentDetailResponse(
                                                row.getLong("id"),
                                                row.getLong("leader_id"),
                                                row.getString("title"),
                                                row.getString("category"),
                                                row.getString("description"),
                                                row.getString("goal"),
                                                row.getString("method"),
                                                row.getString("meeting_type"),
                                                row.getString("location"),
                                                row.getString("online_link"),
                                                row.getString("meeting_day"),
                                                row.getInt("capacity"),
                                                row.getObject(
                                                        "recruitment_deadline",
                                                        java.time.LocalDate.class),
                                                row.getString("expected_duration"),
                                                row.getString("conditions"),
                                                RecruitmentStatus.valueOf(row.getString("status")),
                                                RecruitmentVisibility.valueOf(
                                                        row.getString("visibility")),
                                                row.getObject("group_id", Long.class),
                                                row.getTimestamp("created_at").toLocalDateTime(),
                                                row.getTimestamp("updated_at").toLocalDateTime(),
                                                List.of()))
                        .optional()
                        .orElse(null);
        if (detail == null) {
            return null;
        }
        List<AdminRecruitmentDetailResponse.RecentAction> recentActions =
                jdbcClient
                        .sql(
                                """
                                select action, reason, created_at
                                from admin_audit_logs
                                where target_type = :targetType and target_id = :recruitmentId
                                order by created_at desc, id desc
                                limit 10
                                """)
                        .param("targetType", AdminTargetType.RECRUITMENT.name())
                        .param("recruitmentId", recruitmentId)
                        .query(
                                (row, rowNum) ->
                                        new AdminRecruitmentDetailResponse.RecentAction(
                                                AdminAction.valueOf(row.getString("action")),
                                                row.getString("reason"),
                                                row.getTimestamp("created_at").toLocalDateTime()))
                        .list();
        return new AdminRecruitmentDetailResponse(
                detail.recruitmentId(),
                detail.leaderId(),
                detail.title(),
                detail.category(),
                detail.description(),
                detail.goal(),
                detail.method(),
                detail.meetingType(),
                detail.location(),
                detail.onlineLink(),
                detail.meetingDay(),
                detail.capacity(),
                detail.recruitmentDeadline(),
                detail.expectedDuration(),
                detail.conditions(),
                detail.status(),
                detail.visibility(),
                detail.groupId(),
                detail.createdAt(),
                detail.updatedAt(),
                recentActions);
    }

    private JdbcClient.StatementSpec bind(
            JdbcClient.StatementSpec statement,
            String keyword,
            RecruitmentStatus status,
            RecruitmentVisibility visibility) {
        if (keyword != null && !keyword.isBlank()) {
            statement = statement.param("keyword", "%" + keyword.strip().toLowerCase() + "%");
        }
        if (status != null) {
            statement = statement.param("status", status.name());
        }
        if (visibility != null) {
            statement = statement.param("visibility", visibility.name());
        }
        return statement;
    }
}
