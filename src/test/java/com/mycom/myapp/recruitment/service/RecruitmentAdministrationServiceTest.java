package com.mycom.myapp.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import com.mycom.myapp.recruitment.repository.RecruitmentRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecruitmentAdministrationServiceTest {

    @Mock private RecruitmentRepository recruitmentRepository;

    @InjectMocks private RecruitmentAdministrationService service;

    @Test
    void changesRecruitmentVisibility() {
        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(Member.create("leader@test.com", "encoded", "리더", null, null, null))
                        .status(RecruitmentStatus.RECRUITING)
                        .build();
        when(recruitmentRepository.findById(10L)).thenReturn(Optional.of(post));

        RecruitmentPost changed = service.changeVisibility(10L, RecruitmentVisibility.HIDDEN);

        assertThat(changed.getVisibility()).isEqualTo(RecruitmentVisibility.HIDDEN);
    }
}
