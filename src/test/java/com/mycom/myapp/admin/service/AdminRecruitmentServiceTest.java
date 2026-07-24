package com.mycom.myapp.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.admin.dto.response.AdminRecruitmentDetailResponse;
import com.mycom.myapp.admin.repository.AdminAuditLogRepository;
import com.mycom.myapp.admin.repository.AdminRecruitmentQueryRepository;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import com.mycom.myapp.recruitment.service.port.RecruitmentAdministrationPort;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminRecruitmentServiceTest {

    private final AdminRecruitmentQueryRepository queryRepository =
            mock(AdminRecruitmentQueryRepository.class);
    private final RecruitmentAdministrationPort recruitmentAdministrationPort =
            mock(RecruitmentAdministrationPort.class);
    private final AdminAuditLogRepository auditLogRepository = mock(AdminAuditLogRepository.class);
    private final AdminRecruitmentService service =
            new AdminRecruitmentService(
                    queryRepository, recruitmentAdministrationPort, auditLogRepository);

    @Test
    void returnsCurrentDetailWithoutAuditWhenRequestedVisibilityIsAlreadyCurrent() {
        AdminRecruitmentDetailResponse current = detail(RecruitmentVisibility.HIDDEN);
        when(queryRepository.findRecruitment(10L)).thenReturn(current);

        AdminRecruitmentDetailResponse result =
                service.changeVisibility(
                        1L,
                        10L,
                        RecruitmentVisibility.HIDDEN,
                        RecruitmentVisibility.HIDDEN,
                        "숨김 상태 유지 요청");

        assertThat(result).isSameAs(current);
        verify(recruitmentAdministrationPort, never())
                .changeVisibility(
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(auditLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void hidesRecruitmentAndRecordsAuditLog() {
        AdminRecruitmentDetailResponse current = detail(RecruitmentVisibility.VISIBLE);
        AdminRecruitmentDetailResponse changed = detail(RecruitmentVisibility.HIDDEN);
        when(queryRepository.findRecruitment(10L)).thenReturn(current, changed);

        AdminRecruitmentDetailResponse result =
                service.changeVisibility(
                        1L,
                        10L,
                        RecruitmentVisibility.VISIBLE,
                        RecruitmentVisibility.HIDDEN,
                        "운영 정책 위반으로 숨김 처리");

        assertThat(result.visibility()).isEqualTo(RecruitmentVisibility.HIDDEN);
        verify(recruitmentAdministrationPort).changeVisibility(10L, RecruitmentVisibility.HIDDEN);
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.any());
    }

    private AdminRecruitmentDetailResponse detail(RecruitmentVisibility visibility) {
        return new AdminRecruitmentDetailResponse(
                10L,
                3L,
                "모집글",
                "개발",
                "내용",
                "목표",
                "방법",
                "ONLINE",
                null,
                "https://example.com",
                "매주 화요일",
                5,
                java.time.LocalDate.now().plusDays(7),
                "8주",
                "조건",
                com.mycom.myapp.recruitment.entity.RecruitmentStatus.RECRUITING,
                visibility,
                20L,
                LocalDateTime.of(2026, 7, 23, 10, 0),
                LocalDateTime.of(2026, 7, 23, 10, 0),
                java.util.List.of());
    }
}
