package com.mycom.myapp.member.service;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.member.dto.request.MemberCreateRequest;
import com.mycom.myapp.member.dto.request.MemberUpdateRequest;
import com.mycom.myapp.member.dto.response.MemberResponse;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public MemberResponse create(MemberCreateRequest request) {
        validateUniqueEmail(request.email());
        validateUniqueNickname(request.nickname());

        Member member =
                Member.create(
                        request.email(),
                        passwordEncoder.encode(request.password()),
                        request.nickname(),
                        request.bio(),
                        request.interests(),
                        request.profileImageUrl());
        return MemberResponse.from(memberRepository.save(member));
    }

    public MemberResponse getMe(Long memberId) {
        return MemberResponse.from(getActiveMember(memberId));
    }

    @Transactional
    public MemberResponse updateMe(Long memberId, MemberUpdateRequest request) {
        Member member = getActiveMember(memberId);
        if (request.nickname() != null
                && !request.nickname().equals(member.getNickname())
                && memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        member.updateProfile(
                request.nickname(), request.bio(), request.interests(), request.profileImageUrl());
        return MemberResponse.from(member);
    }

    public Member getActiveMember(Long memberId) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getStatus().name().equals("WITHDRAWN")) {
            throw new BusinessException(ErrorCode.WITHDRAWN_MEMBER);
        }
        return member;
    }

    private void validateUniqueEmail(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void validateUniqueNickname(String nickname) {
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }
}
