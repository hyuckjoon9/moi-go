package com.mycom.myapp.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminOperationsQueryRepositoryTest {

    @Autowired private JdbcClient jdbcClient;
    @Autowired private AdminOperationsQueryRepository repository;

    @Test
    void findsReadOnlyOperationsByGroupStatusAndKeyword() throws Exception {
        jdbcClient
                .sql(
                        """
                        insert into users (email, password, nickname, role, status, created_at, updated_at)
                        values ('admin-operations@example.com', 'encoded', '운영자', 'ADMIN', 'ACTIVE', now(), now()),
                               ('member-operations@example.com', 'encoded', '참여자', 'USER', 'ACTIVE', now(), now())
                        """)
                .update();
        jdbcClient
                .sql(
                        """
                        insert into recruitment_posts (leader_id, title, status, visibility, created_at, updated_at)
                        values ((select id from users where email = 'admin-operations@example.com'), '운영 조회 모집글', 'ACTIVE', 'VISIBLE', now(), now())
                        """)
                .update();
        jdbcClient
                .sql(
                        """
                        insert into study_groups (post_id, name, status, created_at)
                        values ((select id from recruitment_posts where title = '운영 조회 모집글'), '운영 조회 그룹', 'ACTIVE', now())
                        """)
                .update();
        jdbcClient
                .sql(
                        """
                        insert into group_members (group_id, user_id, role, status, joined_at)
                        values ((select id from study_groups where name = '운영 조회 그룹'),
                                (select id from users where email = 'member-operations@example.com'), 'MEMBER', 'ACTIVE', now())
                        """)
                .update();
        jdbcClient
                .sql(
                        """
                        insert into study_schedules (group_id, creator_id, title, scheduled_at, created_at, updated_at)
                        values ((select id from study_groups where name = '운영 조회 그룹'),
                                (select id from users where email = 'admin-operations@example.com'), '운영 조회 일정', now(), now(), now())
                        """)
                .update();

        Object groups = invoke("findGroups", "운영 조회", "ACTIVE", 0, 20);
        Object schedules = invoke("findSchedules", "운영 조회", 0, 20);

        assertThat(items(groups)).hasSize(1);
        assertThat(read(items(groups).getFirst(), "name")).isEqualTo("운영 조회 그룹");
        assertThat(items(schedules)).hasSize(1);
        assertThat(read(items(schedules).getFirst(), "title")).isEqualTo("운영 조회 일정");
    }

    @Test
    void findsAuditLogsWithFiltersAndPagination() throws Exception {
        jdbcClient
                .sql(
                        """
                        insert into users (email, password, nickname, role, status, created_at, updated_at)
                        values ('audit-admin@example.com', 'encoded', '감사운영자', 'ADMIN', 'ACTIVE', now(), now())
                        """)
                .update();
        jdbcClient
                .sql(
                        """
                        insert into admin_audit_logs
                            (admin_id, action, target_type, target_id, target_label, before_snapshot, after_snapshot, reason, created_at)
                        values ((select id from users where email = 'audit-admin@example.com'), 'MEMBER_STATUS_CHANGED',
                                'MEMBER', 100, '대상회원', 'ACTIVE', 'SUSPENDED', '정책 위반 확인', ?)
                        """)
                .param(LocalDateTime.of(2026, 7, 24, 10, 0))
                .update();

        Object logs = invoke("findAuditLogs", "MEMBER_STATUS_CHANGED", "MEMBER", "정책", 0, 20);

        assertThat(items(logs)).hasSize(1);
        assertThat(read(items(logs).getFirst(), "reason")).isEqualTo("정책 위반 확인");
    }

    private Object invoke(String methodName, Object... arguments) throws Exception {
        Method method =
                java.util.Arrays.stream(AdminOperationsQueryRepository.class.getMethods())
                        .filter(candidate -> candidate.getName().equals(methodName))
                        .findFirst()
                        .orElseThrow();
        return method.invoke(repository, arguments);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Object> items(Object response) throws Exception {
        return (java.util.List<Object>) response.getClass().getMethod("items").invoke(response);
    }

    private Object read(Object target, String property) throws Exception {
        return target.getClass().getMethod(property).invoke(target);
    }
}
