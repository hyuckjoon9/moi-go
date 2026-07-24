package com.mycom.myapp.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.auth.repository.RefreshTokenRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.member.entity.MemberStatus;
import com.mycom.myapp.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberAdministrationServiceTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final RefreshTokenRepository refreshTokenRepository =
            mock(RefreshTokenRepository.class);
    private final MemberAdministrationService service =
            new MemberAdministrationService(memberRepository, refreshTokenRepository);

    @Test
    void suspendUserChangesStatusAndRevokesRefreshTokens() {
        Member member = member(10L);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));

        Member changed = service.changeStatus(1L, 10L, MemberStatus.SUSPENDED);

        assertThat(changed.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        verify(refreshTokenRepository).deleteByUserId(10L);
    }

    @Test
    void rejectsAdminWithdrawnAndSelfStatusChanges() {
        Member admin = member(10L);
        ReflectionTestUtils.setField(admin, "role", MemberRole.ADMIN);
        Member withdrawn = member(11L);
        withdrawn.withdraw();
        Member self = member(1L);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(admin));
        when(memberRepository.findById(11L)).thenReturn(Optional.of(withdrawn));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(self));

        assertMemberOperationNotAllowed(
                () -> service.changeStatus(1L, 10L, MemberStatus.SUSPENDED));
        assertMemberOperationNotAllowed(() -> service.changeStatus(1L, 11L, MemberStatus.ACTIVE));
        assertMemberOperationNotAllowed(() -> service.changeStatus(1L, 1L, MemberStatus.SUSPENDED));
    }

    @Test
    void rejectsWithdrawnAsAdministrativeTargetStatus() {
        Member member = member(10L);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.changeStatus(1L, 10L, MemberStatus.WITHDRAWN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertMemberOperationNotAllowed(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ADMIN_MEMBER_OPERATION_NOT_ALLOWED);
    }

    private Member member(Long id) {
        Member member =
                Member.create("user@moigo.test", "encoded-password", "모이고", null, null, null);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
