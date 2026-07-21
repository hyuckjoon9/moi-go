package com.mycom.myapp.application.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycom.myapp.application.entity.JoinApplication;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.repository.RecruitmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class JoinApplicationRepositoryTest {

    @Autowired private JoinApplicationRepository joinApplicationRepository;
    @Autowired private RecruitmentRepository recruitmentRepository;
    @Autowired private MemberRepository memberRepository;

    @Test
    void existsByPostIdAndApplicantIdReturnsTrueWhenAlreadyApplied() {
        Member leader = memberRepository.save(member("leader@test.com", "리더"));
        Member applicant = memberRepository.save(member("applicant@test.com", "지원자"));
        RecruitmentPost post = recruitmentRepository.save(post(leader));
        joinApplicationRepository.save(application(post, applicant));

        assertThat(
                        joinApplicationRepository.existsByPostIdAndApplicantId(
                                post.getId(), applicant.getId()))
                .isTrue();
    }

    @Test
    void rejectsDuplicatePostAndApplicant() {
        Member leader = memberRepository.save(member("leader@test.com", "리더"));
        Member applicant = memberRepository.save(member("applicant@test.com", "지원자"));
        RecruitmentPost post = recruitmentRepository.save(post(leader));
        joinApplicationRepository.saveAndFlush(application(post, applicant));

        assertThatThrownBy(
                        () -> joinApplicationRepository.saveAndFlush(application(post, applicant)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Member member(String email, String nickname) {
        return Member.create(email, "encoded", nickname, null, null, null);
    }

    private RecruitmentPost post(Member leader) {
        return RecruitmentPost.builder()
                .leader(leader)
                .title("스터디")
                .category("개발")
                .status(RecruitmentStatus.RECRUITING)
                .build();
    }

    private JoinApplication application(RecruitmentPost post, Member applicant) {
        return JoinApplication.builder().post(post).applicant(applicant).motivation("지원동기").build();
    }
}
