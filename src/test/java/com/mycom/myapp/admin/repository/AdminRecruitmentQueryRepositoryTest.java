package com.mycom.myapp.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.admin.entity.AdminAction;
import com.mycom.myapp.admin.entity.AdminAuditLog;
import com.mycom.myapp.admin.entity.AdminTargetType;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminRecruitmentQueryRepositoryTest {

    @Autowired private JdbcClient jdbcClient;
    @Autowired private AdminRecruitmentQueryRepository repository;
    @Autowired private AdminAuditLogRepository auditLogRepository;

    @Test
    void findsRecruitmentsByTitleOrLeaderNicknameWithFiltersAndPagination() throws Exception {
        jdbcClient
                .sql(
                        """
                        insert into users (email, password, nickname, role, status, created_at, updated_at)
                        values
                          ('leader-a@example.com', 'encoded', '모집장김', 'USER', 'ACTIVE', now(), now()),
                          ('leader-b@example.com', 'encoded', '다른모집장', 'USER', 'ACTIVE', now(), now())
                        """)
                .update();
        jdbcClient
                .sql(
                        """
                        insert into recruitment_posts
                            (leader_id, title, status, visibility, created_at, updated_at)
                        values
                          ((select id from users where email = 'leader-a@example.com'), '스프링 스터디', 'RECRUITING', 'VISIBLE', '2026-07-20 09:00:00', now()),
                          ((select id from users where email = 'leader-b@example.com'), '자바 모임', 'CLOSED', 'HIDDEN', '2026-07-21 09:00:00', now())
                        """)
                .update();

        Method method =
                java.util.Arrays.stream(AdminRecruitmentQueryRepository.class.getMethods())
                        .filter(candidate -> candidate.getName().equals("findRecruitments"))
                        .findFirst()
                        .orElse(null);

        assertThat(method).isNotNull();
        Object response =
                method.invoke(
                        repository,
                        "모집장김",
                        RecruitmentStatus.RECRUITING,
                        RecruitmentVisibility.VISIBLE,
                        0,
                        1);
        List<?> items = (List<?>) response.getClass().getMethod("items").invoke(response);

        assertThat(response.getClass().getMethod("totalElements").invoke(response)).isEqualTo(1L);
        assertThat(items)
                .singleElement()
                .satisfies(item -> assertThat(read(item, "title")).isEqualTo("스프링 스터디"));
    }

    @Test
    void includesRecentAuditActionsInRecruitmentDetail() throws Exception {
        jdbcClient
                .sql(
                        """
                        insert into users (email, password, nickname, role, status, created_at, updated_at)
                        values ('audit-leader@example.com', 'encoded', '모집장', 'ADMIN', 'ACTIVE', now(), now())
                        """)
                .update();
        jdbcClient
                .sql(
                        """
                        insert into recruitment_posts
                            (leader_id, title, status, visibility, created_at, updated_at)
                        values ((select id from users where email = 'audit-leader@example.com'),
                                '이력 확인 모집글', 'RECRUITING', 'HIDDEN', now(), now())
                        """)
                .update();
        Long recruitmentId =
                jdbcClient
                        .sql("select id from recruitment_posts where title = '이력 확인 모집글'")
                        .query(Long.class)
                        .single();
        auditLogRepository.save(
                AdminAuditLog.create(
                        1L,
                        AdminAction.RECRUITMENT_HIDDEN,
                        AdminTargetType.RECRUITMENT,
                        recruitmentId,
                        "이력 확인 모집글",
                        "{\"visibility\":\"VISIBLE\"}",
                        "{\"visibility\":\"HIDDEN\"}",
                        "운영 정책에 따라 숨김 처리"));

        Object detail = repository.findRecruitment(recruitmentId);
        List<?> recentActions =
                (List<?>) detail.getClass().getMethod("recentActions").invoke(detail);

        assertThat(recentActions)
                .singleElement()
                .satisfies(
                        action -> {
                            assertThat(read(action, "action"))
                                    .isEqualTo(AdminAction.RECRUITMENT_HIDDEN);
                            assertThat(read(action, "reason")).isEqualTo("운영 정책에 따라 숨김 처리");
                        });
    }

    private Object read(Object target, String property) {
        try {
            return target.getClass().getMethod(property).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
