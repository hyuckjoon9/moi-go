package com.mycom.myapp.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.admin.dto.response.AdminMemberDetailResponse;
import com.mycom.myapp.admin.dto.response.AdminMemberListResponse;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.member.entity.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminMemberQueryRepositoryTest {

    @Autowired private JdbcClient jdbcClient;
    @Autowired private AdminMemberQueryRepository repository;

    @Test
    void findsMembersByKeywordRoleAndStatusInCreatedOrder() {
        jdbcClient
                .sql(
                        """
                        insert into users (email, password, nickname, role, status, created_at, updated_at)
                        values
                          ('old@example.com', 'encoded', '김이전', 'USER', 'ACTIVE', '2026-07-20 09:00:00', now()),
                          ('new@example.com', 'encoded', '김최신', 'USER', 'ACTIVE', '2026-07-21 09:00:00', now()),
                          ('admin@example.com', 'encoded', '김관리자', 'ADMIN', 'ACTIVE', '2026-07-22 09:00:00', now())
                        """)
                .update();

        AdminMemberListResponse result =
                repository.findMembers("김", MemberRole.USER, MemberStatus.ACTIVE, 0, 1);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.items())
                .singleElement()
                .satisfies(
                        member -> {
                            assertThat(member.email()).isEqualTo("new@example.com");
                            assertThat(member.status()).isEqualTo(MemberStatus.ACTIVE);
                        });
    }

    @Test
    void findsMemberDetailWithoutCredentialFields() {
        jdbcClient
                .sql(
                        """
                        insert into users
                            (email, password, nickname, bio, interests, profile_image_url, role, status, created_at, updated_at)
                        values
                            ('detail@example.com', 'secret', '상세회원', '소개', 'Java', '/profile.png', 'USER', 'ACTIVE', now(), now())
                        """)
                .update();
        Long memberId =
                jdbcClient
                        .sql("select id from users where email = 'detail@example.com'")
                        .query(Long.class)
                        .single();

        AdminMemberDetailResponse result = repository.findMember(memberId);

        assertThat(result.memberId()).isEqualTo(memberId);
        assertThat(result.email()).isEqualTo("detail@example.com");
        assertThat(result.nickname()).isEqualTo("상세회원");
        assertThat(result.groups()).isEmpty();
        assertThat(result.recentActions()).isEmpty();
    }
}
