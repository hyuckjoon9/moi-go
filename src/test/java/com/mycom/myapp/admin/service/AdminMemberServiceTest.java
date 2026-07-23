package com.mycom.myapp.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.admin.dto.response.AdminMemberDetailResponse;
import com.mycom.myapp.admin.repository.AdminAuditLogRepository;
import com.mycom.myapp.admin.repository.AdminMemberQueryRepository;
import com.mycom.myapp.member.entity.MemberStatus;
import com.mycom.myapp.member.service.port.MemberAdministrationPort;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminMemberServiceTest {

    private final AdminMemberQueryRepository queryRepository =
            mock(AdminMemberQueryRepository.class);
    private final MemberAdministrationPort memberAdministrationPort =
            mock(MemberAdministrationPort.class);
    private final AdminAuditLogRepository auditLogRepository = mock(AdminAuditLogRepository.class);
    private final AdminMemberService service =
            new AdminMemberService(queryRepository, memberAdministrationPort, auditLogRepository);

    @Test
    void returnsCurrentDetailWithoutAuditWhenRequestedStatusIsAlreadyCurrent() {
        AdminMemberDetailResponse current = detail(MemberStatus.SUSPENDED);
        when(queryRepository.findMember(10L)).thenReturn(current);

        AdminMemberDetailResponse result =
                service.changeStatus(
                        1L, 10L, MemberStatus.SUSPENDED, MemberStatus.SUSPENDED, "정지 유지 요청");

        assertThat(result).isSameAs(current);
        verify(memberAdministrationPort, never())
                .changeStatus(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any());
        verify(auditLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private AdminMemberDetailResponse detail(MemberStatus status) {
        return new AdminMemberDetailResponse(
                10L,
                "user@example.com",
                "회원",
                null,
                null,
                null,
                com.mycom.myapp.member.entity.MemberRole.USER,
                status,
                LocalDateTime.of(2026, 7, 20, 10, 0),
                LocalDateTime.of(2026, 7, 20, 10, 0),
                List.of(),
                List.of());
    }
}
