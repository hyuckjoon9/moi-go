package com.mycom.myapp.admin.repository;

import com.mycom.myapp.admin.dto.response.AdminOperationsResponse;
import com.mycom.myapp.admin.dto.response.AdminPageResponse;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminOperationsQueryRepository {

    private final JdbcClient jdbcClient;

    public AdminOperationsQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public AdminPageResponse<AdminOperationsResponse.GroupItem> findGroups(
            String keyword, String status, int page, int size) {
        String where =
                " where (:keyword = '' or lower(g.name) like :pattern) and (:status = '' or g.status = :status) ";
        long total = count("select count(*) from study_groups g" + where, keyword, status);
        List<AdminOperationsResponse.GroupItem> items =
                jdbcClient
                        .sql(
                                """
                                select g.id, g.post_id, g.name, g.status, g.created_at,
                                       count(gm.id) as active_member_count
                                from study_groups g
                                left join group_members gm on gm.group_id = g.id and gm.status = 'ACTIVE'
                                """
                                        + where
                                        + " group by g.id, g.post_id, g.name, g.status, g.created_at order by g.id desc limit :size offset :offset")
                        .param("keyword", normalized(keyword))
                        .param("pattern", pattern(keyword))
                        .param("status", normalized(status))
                        .param("size", size)
                        .param("offset", page * size)
                        .query(
                                (row, rowNum) ->
                                        new AdminOperationsResponse.GroupItem(
                                                row.getLong("id"),
                                                row.getLong("post_id"),
                                                row.getString("name"),
                                                row.getString("status"),
                                                row.getLong("active_member_count"),
                                                time(row.getTimestamp("created_at"))))
                        .list();
        return AdminPageResponse.of(items, page, size, total);
    }

    public AdminPageResponse<AdminOperationsResponse.ScheduleItem> findSchedules(
            String keyword, int page, int size) {
        String where =
                " where (:keyword = '' or lower(s.title) like :pattern or lower(g.name) like :pattern) ";
        long total =
                count(
                        "select count(*) from study_schedules s join study_groups g on g.id = s.group_id"
                                + where,
                        keyword,
                        "");
        List<AdminOperationsResponse.ScheduleItem> items =
                jdbcClient
                        .sql(
                                """
                select s.id, s.group_id, g.name as group_name, s.creator_id, s.title, s.scheduled_at, s.response_deadline
                from study_schedules s join study_groups g on g.id = s.group_id
                """
                                        + where
                                        + " order by s.scheduled_at desc, s.id desc limit :size offset :offset")
                        .param("keyword", normalized(keyword))
                        .param("pattern", pattern(keyword))
                        .param("size", size)
                        .param("offset", page * size)
                        .query(
                                (row, rowNum) ->
                                        new AdminOperationsResponse.ScheduleItem(
                                                row.getLong("id"),
                                                row.getLong("group_id"),
                                                row.getString("group_name"),
                                                row.getLong("creator_id"),
                                                row.getString("title"),
                                                time(row.getTimestamp("scheduled_at")),
                                                time(row.getTimestamp("response_deadline"))))
                        .list();
        return AdminPageResponse.of(items, page, size, total);
    }

    public AdminPageResponse<AdminOperationsResponse.AttendanceItem> findAttendanceRecords(
            String keyword, String status, int page, int size) {
        String where =
                " where (:keyword = '' or lower(s.title) like :pattern or lower(g.name) like :pattern or lower(u.nickname) like :pattern) and (:status = '' or ar.status = :status) ";
        String from =
                " from attendance_records ar join study_schedules s on s.id = ar.schedule_id join study_groups g on g.id = s.group_id join users u on u.id = ar.user_id";
        long total = count("select count(*)" + from + where, keyword, status);
        List<AdminOperationsResponse.AttendanceItem> items =
                jdbcClient
                        .sql(
                                "select ar.id, ar.schedule_id, s.title as schedule_title, s.group_id, g.name as group_name, ar.user_id, u.nickname, ar.status, ar.checked_by, ar.checked_at"
                                        + from
                                        + where
                                        + " order by ar.checked_at desc, ar.id desc limit :size offset :offset")
                        .param("keyword", normalized(keyword))
                        .param("pattern", pattern(keyword))
                        .param("status", normalized(status))
                        .param("size", size)
                        .param("offset", page * size)
                        .query(
                                (row, rowNum) ->
                                        new AdminOperationsResponse.AttendanceItem(
                                                row.getLong("id"),
                                                row.getLong("schedule_id"),
                                                row.getString("schedule_title"),
                                                row.getLong("group_id"),
                                                row.getString("group_name"),
                                                row.getLong("user_id"),
                                                row.getString("nickname"),
                                                row.getString("status"),
                                                row.getLong("checked_by"),
                                                time(row.getTimestamp("checked_at"))))
                        .list();
        return AdminPageResponse.of(items, page, size, total);
    }

    public AdminPageResponse<AdminOperationsResponse.ActivityItem> findActivityRecords(
            String keyword, int page, int size) {
        String where =
                " where (:keyword = '' or lower(a.topic) like :pattern or lower(s.title) like :pattern or lower(g.name) like :pattern) ";
        String from =
                " from activity_records a join study_schedules s on s.id = a.schedule_id join study_groups g on g.id = s.group_id";
        long total = count("select count(*)" + from + where, keyword, "");
        List<AdminOperationsResponse.ActivityItem> items =
                jdbcClient
                        .sql(
                                "select a.id, a.schedule_id, s.title as schedule_title, s.group_id, g.name as group_name, a.author_id, a.topic, a.created_at, a.updated_at"
                                        + from
                                        + where
                                        + " order by a.updated_at desc, a.id desc limit :size offset :offset")
                        .param("keyword", normalized(keyword))
                        .param("pattern", pattern(keyword))
                        .param("size", size)
                        .param("offset", page * size)
                        .query(
                                (row, rowNum) ->
                                        new AdminOperationsResponse.ActivityItem(
                                                row.getLong("id"),
                                                row.getLong("schedule_id"),
                                                row.getString("schedule_title"),
                                                row.getLong("group_id"),
                                                row.getString("group_name"),
                                                row.getLong("author_id"),
                                                row.getString("topic"),
                                                time(row.getTimestamp("created_at")),
                                                time(row.getTimestamp("updated_at"))))
                        .list();
        return AdminPageResponse.of(items, page, size, total);
    }

    public AdminPageResponse<AdminOperationsResponse.AuditLogItem> findAuditLogs(
            String action, String targetType, String keyword, int page, int size) {
        String where =
                " where (:action = '' or action = :action) and (:targetType = '' or target_type = :targetType) and (:keyword = '' or lower(target_label) like :pattern or lower(reason) like :pattern) ";
        long total =
                count(
                        "select count(*) from admin_audit_logs" + where,
                        keyword,
                        "",
                        action,
                        targetType);
        List<AdminOperationsResponse.AuditLogItem> items =
                jdbcClient
                        .sql(
                                "select id, admin_id, action, target_type, target_id, target_label, before_snapshot, after_snapshot, reason, created_at from admin_audit_logs"
                                        + where
                                        + " order by created_at desc, id desc limit :size offset :offset")
                        .param("action", normalized(action))
                        .param("targetType", normalized(targetType))
                        .param("keyword", normalized(keyword))
                        .param("pattern", pattern(keyword))
                        .param("size", size)
                        .param("offset", page * size)
                        .query(
                                (row, rowNum) ->
                                        new AdminOperationsResponse.AuditLogItem(
                                                row.getLong("id"),
                                                row.getLong("admin_id"),
                                                row.getString("action"),
                                                row.getString("target_type"),
                                                row.getLong("target_id"),
                                                row.getString("target_label"),
                                                row.getString("before_snapshot"),
                                                row.getString("after_snapshot"),
                                                row.getString("reason"),
                                                time(row.getTimestamp("created_at"))))
                        .list();
        return AdminPageResponse.of(items, page, size, total);
    }

    private long count(String sql, String keyword, String status) {
        return count(sql, keyword, status, "", "");
    }

    private long count(
            String sql, String keyword, String status, String action, String targetType) {
        return jdbcClient
                .sql(sql)
                .param("keyword", normalized(keyword))
                .param("pattern", pattern(keyword))
                .param("status", normalized(status))
                .param("action", normalized(action))
                .param("targetType", normalized(targetType))
                .query(Long.class)
                .single();
    }

    private String normalized(String value) {
        return value == null ? "" : value.strip();
    }

    private String pattern(String value) {
        return "%" + normalized(value).toLowerCase() + "%";
    }

    private static java.time.LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
