package com.mycom.myapp.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.admin.dto.response.AdminDashboardResponse;
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
class AdminDashboardQueryRepositoryTest {

    @Autowired private JdbcClient jdbcClient;
    @Autowired private AdminDashboardQueryRepository repository;

    @Test
    void aggregatesMemberRecruitmentAndGroupCounts() {
        jdbcClient
                .sql(
                        """
                        insert into users
                            (email, password, nickname, role, status, created_at, updated_at)
                        values
                            ('active@test.com', 'encoded', '활성회원', 'USER', 'ACTIVE', now(), now()),
                            ('withdrawn@test.com', 'encoded', '탈퇴회원', 'USER', 'WITHDRAWN', now(), now())
                        """)
                .update();

        Long leaderId =
                jdbcClient
                        .sql("select id from users where email = 'active@test.com'")
                        .query(Long.class)
                        .single();
        jdbcClient
                .sql(
                        """
                        insert into recruitment_posts
                            (leader_id, title, category, meeting_type, capacity,
                             recruitment_deadline, status, created_at, updated_at)
                        values
                            (:leaderId, '모집 중', '개발', 'ONLINE', 4,
                             current_date, 'RECRUITING', now(), now()),
                            (:leaderId, '진행 중', '개발', 'ONLINE', 4,
                             current_date, 'ACTIVE', now(), now())
                        """)
                .param("leaderId", leaderId)
                .update();
        List<Long> postIds =
                jdbcClient
                        .sql("select id from recruitment_posts order by id")
                        .query(Long.class)
                        .list();
        jdbcClient
                .sql(
                        """
                        insert into study_groups (post_id, name, status, created_at)
                        values
                            (:recruitingPostId, '활성 그룹', 'ACTIVE', now()),
                            (:activePostId, '종료 그룹', 'ENDED', now())
                        """)
                .param("recruitingPostId", postIds.get(0))
                .param("activePostId", postIds.get(1))
                .update();

        AdminDashboardResponse result = repository.findDashboard();

        assertThat(result.members().total()).isEqualTo(2);
        assertThat(result.members().active()).isEqualTo(1);
        assertThat(result.members().suspended()).isZero();
        assertThat(result.members().withdrawn()).isEqualTo(1);
        assertThat(result.recruitments().recruiting()).isEqualTo(1);
        assertThat(result.recruitments().active()).isEqualTo(1);
        assertThat(result.groups().active()).isEqualTo(1);
        assertThat(result.groups().ended()).isEqualTo(1);
        assertThat(result.recentActions()).isEmpty();
    }
}
