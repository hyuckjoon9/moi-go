package com.mycom.myapp.recruitment.service;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.recruitment.dto.request.RecruitmentCreateRequest;
import com.mycom.myapp.recruitment.dto.response.RecruitmentResponse;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.repository.RecruitmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecruitmentService {

    private final RecruitmentRepository recruitmentRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public RecruitmentResponse create(Long leaderId, RecruitmentCreateRequest request) {
        Member leader =
                memberRepository
                        .findById(leaderId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .title(request.title())
                        .category(request.category())
                        .description(request.description())
                        .goal(request.goal())
                        .method(request.method())
                        .meetingType(request.meetingType())
                        .location(request.location())
                        .onlineLink(request.onlineLink())
                        .meetingDay(request.meetingDay())
                        .capacity(request.capacity())
                        .recruitmentDeadline(request.recruitmentDeadline())
                        .expectedDuration(request.expectedDuration())
                        .conditions(request.conditions())
                        .status(RecruitmentStatus.RECRUITING)
                        .build();

        return RecruitmentResponse.from(recruitmentRepository.save(post));
    }
}
