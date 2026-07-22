package com.mycom.myapp.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.recruitment.dto.request.RecruitmentUpdateRequest;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.repository.RecruitmentRepository;
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

    @InjectMocks private RecruitmentService recruitmentService;

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

        org.mockito.Mockito.verify(recruitmentRepository).delete(post);
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
    @DisplayName("리더가 스터디를 종료하면 상태가 ENDED로 바뀐다")
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
    }
}
