package com.mycom.myapp.admin.repository;

import com.mycom.myapp.admin.dto.response.AdminDashboardResponse;
import com.mycom.myapp.admin.entity.AdminAction;
import com.mycom.myapp.admin.entity.AdminTargetType;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminDashboardQueryRepository {

    private static final String METRICS_SQL =
            """
            select
              (select count(*) from users) as member_total,
              (select count(*) from users where status = 'ACTIVE') as member_active,
              (select count(*) from users where status = 'SUSPENDED') as member_suspended,
              (select count(*) from users where status = 'WITHDRAWN') as member_withdrawn,
              (select count(*) from recruitment_posts where status = 'RECRUITING') as recruitment_recruiting,
              (select count(*) from recruitment_posts where status = 'CLOSED') as recruitment_closed,
              (select count(*) from recruitment_posts where status = 'ACTIVE') as recruitment_active,
              (select count(*) from recruitment_posts where status = 'ENDED') as recruitment_ended,
              (select count(*) from study_groups where status = 'ACTIVE') as group_active,
              (select count(*) from study_groups where status = 'ENDED') as group_ended
            """;

    private final JdbcClient jdbcClient;

    public AdminDashboardQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public AdminDashboardResponse findDashboard() {
        Metrics metrics =
                jdbcClient
                        .sql(METRICS_SQL)
                        .query(
                                (row, rowNum) ->
                                        new Metrics(
                                                row.getLong("member_total"),
                                                row.getLong("member_active"),
                                                row.getLong("member_suspended"),
                                                row.getLong("member_withdrawn"),
                                                row.getLong("recruitment_recruiting"),
                                                row.getLong("recruitment_closed"),
                                                row.getLong("recruitment_active"),
                                                row.getLong("recruitment_ended"),
                                                row.getLong("group_active"),
                                                row.getLong("group_ended")))
                        .single();
        List<AdminDashboardResponse.RecentAction> recentActions =
                jdbcClient
                        .sql(
                                """
                                select id, action, target_type, target_id, target_label, admin_id, reason, created_at
                                from admin_audit_logs
                                order by created_at desc, id desc
                                limit 10
                                """)
                        .query(
                                (row, rowNum) ->
                                        new AdminDashboardResponse.RecentAction(
                                                row.getLong("id"),
                                                AdminAction.valueOf(row.getString("action")),
                                                AdminTargetType.valueOf(
                                                        row.getString("target_type")),
                                                row.getLong("target_id"),
                                                row.getString("target_label"),
                                                row.getLong("admin_id"),
                                                row.getString("reason"),
                                                row.getTimestamp("created_at").toLocalDateTime()))
                        .list();
        return new AdminDashboardResponse(
                new AdminDashboardResponse.MemberCounts(
                        metrics.memberTotal(),
                        metrics.memberActive(),
                        metrics.memberSuspended(),
                        metrics.memberWithdrawn()),
                new AdminDashboardResponse.RecruitmentCounts(
                        metrics.recruitmentRecruiting(),
                        metrics.recruitmentClosed(),
                        metrics.recruitmentActive(),
                        metrics.recruitmentEnded()),
                new AdminDashboardResponse.GroupCounts(metrics.groupActive(), metrics.groupEnded()),
                recentActions);
    }

    private record Metrics(
            long memberTotal,
            long memberActive,
            long memberSuspended,
            long memberWithdrawn,
            long recruitmentRecruiting,
            long recruitmentClosed,
            long recruitmentActive,
            long recruitmentEnded,
            long groupActive,
            long groupEnded) {}
}
