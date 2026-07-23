package com.mycom.myapp.admin.repository;

import com.mycom.myapp.admin.dto.response.AdminMemberDetailResponse;
import com.mycom.myapp.admin.dto.response.AdminMemberListResponse;
import com.mycom.myapp.admin.entity.AdminAction;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.member.entity.MemberStatus;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.GroupStatus;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminMemberQueryRepository {

    private final JdbcClient jdbcClient;

    public AdminMemberQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public AdminMemberListResponse findMembers(
            String keyword, MemberRole role, MemberStatus status, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (email like :keyword or nickname like :keyword)");
        }
        if (role != null) {
            where.append(" and role = :role");
        }
        if (status != null) {
            where.append(" and status = :status");
        }

        JdbcClient.StatementSpec countStatement =
                bind(jdbcClient.sql("select count(*) from users" + where), keyword, role, status);
        long totalElements = countStatement.query(Long.class).single();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        List<AdminMemberListResponse.Item> items =
                bind(
                                jdbcClient.sql(
                                        """
                                        select id, email, nickname, role, status, created_at
                                        from users
                                        """
                                                + where
                                                + " order by created_at desc, id desc limit :size offset :offset"),
                                keyword,
                                role,
                                status)
                        .param("size", size)
                        .param("offset", page * size)
                        .query(
                                (row, rowNum) ->
                                        new AdminMemberListResponse.Item(
                                                row.getLong("id"),
                                                row.getString("email"),
                                                row.getString("nickname"),
                                                MemberRole.valueOf(row.getString("role")),
                                                MemberStatus.valueOf(row.getString("status")),
                                                row.getTimestamp("created_at").toLocalDateTime()))
                        .list();
        return new AdminMemberListResponse(items, page, size, totalElements, totalPages);
    }

    public AdminMemberDetailResponse findMember(Long memberId) {
        MemberDetailRow member =
                jdbcClient
                        .sql(
                                """
                                select id, email, nickname, bio, interests, profile_image_url, role, status, created_at, updated_at
                                from users where id = :memberId
                                """)
                        .param("memberId", memberId)
                        .query(
                                (row, rowNum) ->
                                        new MemberDetailRow(
                                                row.getLong("id"),
                                                row.getString("email"),
                                                row.getString("nickname"),
                                                row.getString("bio"),
                                                row.getString("interests"),
                                                row.getString("profile_image_url"),
                                                MemberRole.valueOf(row.getString("role")),
                                                MemberStatus.valueOf(row.getString("status")),
                                                row.getTimestamp("created_at").toLocalDateTime(),
                                                row.getTimestamp("updated_at").toLocalDateTime()))
                        .optional()
                        .orElse(null);
        if (member == null) {
            return null;
        }
        List<AdminMemberDetailResponse.GroupSummary> groups =
                jdbcClient
                        .sql(
                                """
                                select g.id, g.name, gm.role, g.status
                                from group_members gm join study_groups g on g.id = gm.group_id
                                where gm.user_id = :memberId
                                order by gm.joined_at desc, gm.id desc
                                """)
                        .param("memberId", memberId)
                        .query(
                                (row, rowNum) ->
                                        new AdminMemberDetailResponse.GroupSummary(
                                                row.getLong("id"),
                                                row.getString("name"),
                                                GroupRole.valueOf(row.getString("role")),
                                                GroupStatus.valueOf(row.getString("status"))))
                        .list();
        List<AdminMemberDetailResponse.RecentAction> recentActions =
                jdbcClient
                        .sql(
                                """
                                select action, reason, created_at from admin_audit_logs
                                where target_type = 'MEMBER' and target_id = :memberId
                                order by created_at desc, id desc limit 10
                                """)
                        .param("memberId", memberId)
                        .query(
                                (row, rowNum) ->
                                        new AdminMemberDetailResponse.RecentAction(
                                                AdminAction.valueOf(row.getString("action")),
                                                row.getString("reason"),
                                                row.getTimestamp("created_at").toLocalDateTime()))
                        .list();
        return new AdminMemberDetailResponse(
                member.id(),
                member.email(),
                member.nickname(),
                member.bio(),
                member.interests(),
                member.profileImageUrl(),
                member.role(),
                member.status(),
                member.createdAt(),
                member.updatedAt(),
                groups,
                recentActions);
    }

    private JdbcClient.StatementSpec bind(
            JdbcClient.StatementSpec statement,
            String keyword,
            MemberRole role,
            MemberStatus status) {
        if (keyword != null && !keyword.isBlank()) {
            statement = statement.param("keyword", "%" + keyword.strip() + "%");
        }
        if (role != null) {
            statement = statement.param("role", role.name());
        }
        if (status != null) {
            statement = statement.param("status", status.name());
        }
        return statement;
    }

    private record MemberDetailRow(
            Long id,
            String email,
            String nickname,
            String bio,
            String interests,
            String profileImageUrl,
            MemberRole role,
            MemberStatus status,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt) {}
}
