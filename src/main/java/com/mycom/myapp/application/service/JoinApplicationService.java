package com.mycom.myapp.application.service;

import com.mycom.myapp.application.dto.request.JoinApplicationCreateRequest;
import com.mycom.myapp.application.dto.response.JoinApplicationResponse;
import com.mycom.myapp.application.entity.JoinApplication;
import com.mycom.myapp.application.repository.JoinApplicationRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.repository.RecruitmentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JoinApplicationService {

    private final JoinApplicationRepository joinApplicationRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public JoinApplicationResponse create(
            Long postId, Long applicantId, JoinApplicationCreateRequest request) {
        RecruitmentPost post =
                recruitmentRepository
                        .findById(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));
        if (post.getLeader().getId().equals(applicantId)) {
            throw new BusinessException(ErrorCode.SELF_APPLICATION_NOT_ALLOWED);
        }

        if (post.getStatus() != RecruitmentStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.RECRUITMENT_CLOSED);
        }

        if (joinApplicationRepository.existsByPostIdAndApplicantId(postId, applicantId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_APPLICATION);
        }

        Member applicant =
                memberRepository
                        .findById(applicantId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        JoinApplication application =
                JoinApplication.builder()
                        .post(post)
                        .applicant(applicant)
                        .motivation(request.motivation())
                        .experience(request.experience())
                        .availableTime(request.availableTime())
                        .desiredRole(request.desiredRole())
                        .build();

        return JoinApplicationResponse.from(joinApplicationRepository.save(application));
    }
}
