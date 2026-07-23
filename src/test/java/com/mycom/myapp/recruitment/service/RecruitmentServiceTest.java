package com.mycom.myapp.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.recruitment.dto.request.RecruitmentCreateRequest;
import com.mycom.myapp.recruitment.dto.request.RecruitmentUpdateRequest;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.repository.RecruitmentRepository;
import com.mycom.myapp.study.service.CreateStudyGroupCommand;
import com.mycom.myapp.study.service.port.StudyGroupProvisioningPort;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecruitmentServiceTest {

    @Mock private RecruitmentRepository recruitmentRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private StudyGroupProvisioningPort studyGroupProvisioningPort;

    @InjectMocks private RecruitmentService recruitmentService;

    @Test
    @DisplayName("모집글을 작성하면 그룹 생성도 함께 요청한다")
    void create_success() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentCreateRequest request =
                new RecruitmentCreateRequest(
                        "제목",
                        "개발",
                        "설명",
                        "목표",
                        "방법",
                        "ONLINE",
                        null,
                        "http://link",
                        "매주 화요일",
                        5,
                        java.time.LocalDate.now().plusDays(7),
                        "8주",
                        "조건");

        when(memberRepository.findById(1L)).thenReturn(Optional.of(leader));
        when(recruitmentRepository.save(any(RecruitmentPost.class)))
                .thenAnswer(
                        invocation -> {
                            RecruitmentPost saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 10L); // [추가] 실제 DB처럼 id를 채워줌
                            return saved;
                        });

        var response = recruitmentService.create(1L, request);

        assertThat(response.title()).isEqualTo("제목");
        verify(studyGroupProvisioningPort).createGroup(any(CreateStudyGroupCommand.class));
    }

    @Test
    @DisplayName("리더가 모집글을 수정하면 반영된다")
    void update_success() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .title("원래 제목")
                        .status(RecruitmentStatus.RECRUITING)
                        .build();

        RecruitmentUpdateRequest request =
                new RecruitmentUpdateRequest(
                        "수정된 제목",
                        "개발",
                        "수정된 설명",
                        "목표",
                        "방법",
                        "ONLINE",
                        null,
                        "http://link",
                        "매주 화요일",
                        5,
                        java.time.LocalDate.now().plusDays(7),
                        "8주",
                        "조건");

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));

        var response = recruitmentService.update(1L, 1L, request);

        assertThat(response.title()).isEqualTo("수정된 제목");
    }

    @Test
    @DisplayName("리더가 아닌 사용자가 수정을 시도하면 예외가 발생한다")
    void update_fail_notLeader() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .capacity(5)
                        .status(RecruitmentStatus.RECRUITING)
                        .build();

        RecruitmentUpdateRequest request =
                new RecruitmentUpdateRequest(
                        "제목",
                        "개발",
                        "설명",
                        "목표",
                        "방법",
                        "ONLINE",
                        null,
                        "http://link",
                        "매주 화요일",
                        5,
                        java.time.LocalDate.now().plusDays(7),
                        "8주",
                        "조건");

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> recruitmentService.update(1L, 999L, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("리더가 모집글을 삭제하면 저장소에서 제거된다")
    void delete_success() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .status(RecruitmentStatus.RECRUITING)
                        .build();

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));

        recruitmentService.delete(1L, 1L);

        verify(recruitmentRepository).delete(post);
    }

    @Test
    @DisplayName("리더가 모집을 마감하면 상태가 CLOSED로 바뀐다")
    void close_success() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .capacity(5)
                        .status(RecruitmentStatus.RECRUITING)
                        .build();

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));

        var response = recruitmentService.close(1L, 1L);

        assertThat(response.status()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("리더가 스터디를 종료하면 상태가 ENDED로 바뀌고 그룹도 종료 요청한다")
    void end_success() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .capacity(5)
                        .status(RecruitmentStatus.ACTIVE)
                        .build();

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));

        var response = recruitmentService.end(1L, 1L);

        assertThat(response.status()).isEqualTo("ENDED");
        verify(studyGroupProvisioningPort).endGroup(1L);
    }
    
    @Test
    @DisplayName("리더가 마감된 모집글을 재모집하면 내용이 갱신되고 상태가 RECRUITING으로 바뀐다")
    void reopen_success() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .title("원래 제목")
                        .capacity(5)
                        .status(RecruitmentStatus.CLOSED)
                        .build();

        RecruitmentUpdateRequest request =
                new RecruitmentUpdateRequest(
                        "재모집 제목",
                        "개발",
                        "설명",
                        "목표",
                        "방법",
                        "ONLINE",
                        null,
                        "http://link",
                        "매주 화요일",
                        6, // 추가: 재모집하면서 인원도 같이 늘림
                        java.time.LocalDate.now().plusDays(7),
                        "8주",
                        "조건");

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));

        var response = recruitmentService.reopen(1L, 1L, request);

        assertThat(response.status()).isEqualTo("RECRUITING");
        assertThat(response.title()).isEqualTo("재모집 제목");
        assertThat(response.capacity()).isEqualTo(6);
    }

    @Test
    @DisplayName("CLOSED 상태가 아닌 모집글은 재모집할 수 없다")
    void reopen_fail_notClosed() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .capacity(5)
                        .status(RecruitmentStatus.RECRUITING) // 추가: CLOSED가 아닌 상태
                        .build();

        RecruitmentUpdateRequest request =
                new RecruitmentUpdateRequest(
                        "제목",
                        "개발",
                        "설명",
                        "목표",
                        "방법",
                        "ONLINE",
                        null,
                        "http://link",
                        "매주 화요일",
                        5,
                        java.time.LocalDate.now().plusDays(7),
                        "8주",
                        "조건");

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> recruitmentService.reopen(1L, 1L, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("리더가 아닌 사용자가 재모집을 시도하면 예외가 발생한다")
    void reopen_fail_notLeader() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);
        ReflectionTestUtils.setField(leader, "id", 1L);

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .capacity(5)
                        .status(RecruitmentStatus.CLOSED)
                        .build();

        RecruitmentUpdateRequest request =
                new RecruitmentUpdateRequest(
                        "제목",
                        "개발",
                        "설명",
                        "목표",
                        "방법",
                        "ONLINE",
                        null,
                        "http://link",
                        "매주 화요일",
                        5,
                        java.time.LocalDate.now().plusDays(7),
                        "8주",
                        "조건");

        when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> recruitmentService.reopen(1L, 999L, request)) // 추가: 리더(1L)가 아닌 999L로 호출
                .isInstanceOf(BusinessException.class);
    }
}
