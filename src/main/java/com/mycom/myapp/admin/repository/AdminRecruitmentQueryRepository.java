package com.mycom.myapp.admin.repository;

import com.mycom.myapp.admin.dto.response.AdminRecruitmentDetailResponse;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRecruitmentQueryRepository {

    private final JdbcClient jdbcClient;

    public AdminRecruitmentQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public AdminRecruitmentDetailResponse findRecruitment(Long recruitmentId) {
        return jdbcClient
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
                                                "recruitment_deadline", java.time.LocalDate.class),
                                        row.getString("expected_duration"),
                                        row.getString("conditions"),
                                        RecruitmentStatus.valueOf(row.getString("status")),
                                        RecruitmentVisibility.valueOf(row.getString("visibility")),
                                        row.getObject("group_id", Long.class),
                                        row.getTimestamp("created_at").toLocalDateTime(),
                                        row.getTimestamp("updated_at").toLocalDateTime()))
                .optional()
                .orElse(null);
    }
}
