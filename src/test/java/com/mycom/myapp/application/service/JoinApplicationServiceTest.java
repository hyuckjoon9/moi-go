package com.mycom.myapp.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.application.dto.request.JoinApplicationCreateRequest;
import com.mycom.myapp.application.entity.ApplicationStatus;
import com.mycom.myapp.application.entity.JoinApplication;
import com.mycom.myapp.application.repository.JoinApplicationRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.repository.RecruitmentRepository;
import com.mycom.myapp.study.service.AddStudyGroupMemberCommand;
import com.mycom.myapp.study.service.port.StudyGroupProvisioningPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JoinApplicationServiceTest {

    @Mock private JoinApplicationRepository joinApplicationRepository;
    @Mock private RecruitmentRepository recruitmentRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private StudyGroupProvisioningPort studyGroupProvisioningPort;

    @InjectMocks private JoinApplicationService joinApplicationService;

    @Test
    @DisplayName("정상적으로 지원하면 저장된다")
    void create_success() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);
        Member applicant = Member.create("applicant@test.com", "encoded", "지원자", null, null, null);

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .title("제목")
                        .status(RecruitmentStatus.RECRUITING)
                        .build();

        JoinApplicationCreateRequest request =
                new JoinApplicationCreateRequest("지원동기", "경험", "주말 가능", "백엔드");

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));
        when(joinApplicationRepository.existsByPostIdAndApplicantId(1L, 2L)).thenReturn(false);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(applicant));
        when(joinApplicationRepository.save(any(JoinApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = joinApplicationService.create(1L, 2L, request);

        assertThat(response.motivation()).isEqualTo("지원동기");
    }

    @Test
    @DisplayName("이미 지원한 모집글에 재지원하면 예외가 발생한다")
    void create_fail_duplicate() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder().leader(leader).status(RecruitmentStatus.RECRUITING).build();

        JoinApplicationCreateRequest request =
                new JoinApplicationCreateRequest("지원동기", null, null, null);

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));
        when(joinApplicationRepository.existsByPostIdAndApplicantId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> joinApplicationService.create(1L, 2L, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("리더가 아닌 사용자가 지원자 목록을 조회하면 예외가 발생한다")
    void getApplicants_fail_accessDenied() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder().leader(leader).status(RecruitmentStatus.RECRUITING).build();

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> joinApplicationService.getApplicants(1L, 999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("리더가 지원자 목록을 조회하면 정상적으로 반환된다")
    void getApplicants_success() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder().leader(leader).status(RecruitmentStatus.RECRUITING).build();

        when(recruitmentRepository.findById(10L)).thenReturn(Optional.of(post));
        when(joinApplicationRepository.findByPostId(10L)).thenReturn(List.of());

        var result = joinApplicationService.getApplicants(10L, 1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("리더가 PENDING 상태의 지원을 승인하면 상태가 APPROVED로 바뀌고 그룹원 추가를 요청한다")
    void approve_success() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);
        Member applicant = Member.create("applicant@test.com", "encoded", "지원자", null, null, null);
        ReflectionTestUtils.setField(applicant, "id", 2L);

        RecruitmentPost post =
                RecruitmentPost.builder().leader(leader).status(RecruitmentStatus.RECRUITING).build();

        JoinApplication application =
                JoinApplication.builder().post(post).applicant(applicant).motivation("지원동기").build();

        when(recruitmentRepository.findById(10L)).thenReturn(Optional.of(post));
        when(joinApplicationRepository.findByIdAndPostId(100L, 10L)).thenReturn(Optional.of(application));

        var response = joinApplicationService.approve(10L, 100L, 1L);

        assertThat(response.status()).isEqualTo(ApplicationStatus.APPROVED);
        verify(studyGroupProvisioningPort)
                .addMember(new AddStudyGroupMemberCommand(10L, 2L));
    }

    @Test
    @DisplayName("리더가 아닌 사용자가 승인을 시도하면 예외가 발생한다")
    void approve_fail_notLeader() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder().leader(leader).status(RecruitmentStatus.RECRUITING).build();

        when(recruitmentRepository.findById(10L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> joinApplicationService.approve(10L, 100L, 999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("이미 처리된 지원을 다시 승인하려 하면 예외가 발생한다")
    void approve_fail_alreadyProcessed() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);
        Member applicant = Member.create("applicant@test.com", "encoded", "지원자", null, null, null);

        RecruitmentPost post =
                RecruitmentPost.builder().leader(leader).status(RecruitmentStatus.RECRUITING).build();

        JoinApplication application =
                JoinApplication.builder().post(post).applicant(applicant).motivation("지원동기").build();
        application.approve();

        when(recruitmentRepository.findById(10L)).thenReturn(Optional.of(post));
        when(joinApplicationRepository.findByIdAndPostId(100L, 10L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> joinApplicationService.approve(10L, 100L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("리더가 PENDING 상태의 지원을 거절하면 상태가 REJECTED로 바뀐다")
    void reject_success() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);
        Member applicant = Member.create("applicant@test.com", "encoded", "지원자", null, null, null);

        RecruitmentPost post =
                RecruitmentPost.builder().leader(leader).status(RecruitmentStatus.RECRUITING).build();

        JoinApplication application =
                JoinApplication.builder().post(post).applicant(applicant).motivation("지원동기").build();

        when(recruitmentRepository.findById(10L)).thenReturn(Optional.of(post));
        when(joinApplicationRepository.findByIdAndPostId(100L, 10L)).thenReturn(Optional.of(application));

        var response = joinApplicationService.reject(10L, 100L, 1L);

        assertThat(response.status()).isEqualTo(ApplicationStatus.REJECTED);
    }
}