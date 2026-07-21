package com.mycom.myapp.recruitment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class RecruitmentRepositoryTest {

    @Autowired private RecruitmentRepository recruitmentRepository;
    @Autowired private MemberRepository memberRepository;

    @Test
    void findsByCategory() {
        Member leader =
                memberRepository.save(
                        Member.create("leader@test.com", "encoded", "리더", null, null, null));
        recruitmentRepository.save(post(leader, "개발 스터디", "개발"));
        recruitmentRepository.save(post(leader, "디자인 스터디", "디자인"));

        var result = recruitmentRepository.findByCategory("개발", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("개발 스터디");
    }

    private RecruitmentPost post(Member leader, String title, String category) {
        return RecruitmentPost.builder()
                .leader(leader)
                .title(title)
                .category(category)
                .status(RecruitmentStatus.RECRUITING)
                .build();
    }
}
