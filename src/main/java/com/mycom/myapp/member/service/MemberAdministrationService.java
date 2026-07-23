package com.mycom.myapp.member.service;

import com.mycom.myapp.auth.repository.RefreshTokenRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.member.entity.MemberStatus;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.member.service.port.MemberAdministrationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberAdministrationService implements MemberAdministrationPort {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public MemberAdministrationService(
            MemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional
    public Member changeStatus(Long requesterId, Long memberId, MemberStatus status) {
        if (status != MemberStatus.ACTIVE && status != MemberStatus.SUSPENDED) {
            throw new IllegalArgumentException("관리자 상태 변경은 ACTIVE 또는 SUSPENDED만 가능합니다.");
        }
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (memberId.equals(requesterId)
                || member.getRole() == MemberRole.ADMIN
                || member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.ADMIN_MEMBER_OPERATION_NOT_ALLOWED);
        }
        member.changeStatus(status);
        memberRepository.saveAndFlush(member);
        if (status == MemberStatus.SUSPENDED) {
            refreshTokenRepository.deleteByUserId(memberId);
        }
        return member;
    }
}
