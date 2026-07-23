package com.mycom.myapp.recruitment.service;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.recruitment.dto.request.RecruitmentCreateRequest;
import com.mycom.myapp.recruitment.dto.request.RecruitmentUpdateRequest;
import com.mycom.myapp.recruitment.dto.response.RecruitmentResponse;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.repository.RecruitmentRepository;
import com.mycom.myapp.study.service.CreateStudyGroupCommand;
import com.mycom.myapp.study.service.port.StudyGroupProvisioningPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecruitmentService {

    private final RecruitmentRepository recruitmentRepository;
    private final MemberRepository memberRepository;
    private final StudyGroupProvisioningPort studyGroupProvisioningPort;

    @Transactional
    public RecruitmentResponse create(Long leaderId, RecruitmentCreateRequest request) {
        Member leader =
                memberRepository
                        .findById(leaderId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        RecruitmentPost post =
                recruitmentRepository.save(
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
                                .build());

        studyGroupProvisioningPort.createGroup(
                new CreateStudyGroupCommand(post.getId(), post.getTitle(), leaderId, List.of()));

        return RecruitmentResponse.from(post);
    }

    public Page<RecruitmentResponse> getList(String category, Pageable pageable) {

        Page<RecruitmentPost> posts =
                (category == null || category.isBlank())
                        ? recruitmentRepository.findAll(pageable)
                        : recruitmentRepository.findByCategory(category, pageable);
        return posts.map(RecruitmentResponse::from);
    }

    public RecruitmentResponse getDetail(Long id) {
        RecruitmentPost post =
                recruitmentRepository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));
        return RecruitmentResponse.from(post);
    }

    @Transactional
    public RecruitmentResponse update(
            Long postId, Long requesterId, RecruitmentUpdateRequest request) {
        RecruitmentPost post = getPostAsLeader(postId, requesterId);
        post.update(
                request.title(),
                request.category(),
                request.description(),
                request.goal(),
                request.method(),
                request.meetingType(),
                request.location(),
                request.onlineLink(),
                request.meetingDay(),
                request.capacity(),
                request.recruitmentDeadline(),
                request.expectedDuration(),
                request.conditions());
        return RecruitmentResponse.from(post);
    }

    @Transactional
    public void delete(Long postId, Long requesterId) {
        recruitmentRepository.delete(getPostAsLeader(postId, requesterId));
    }

    @Transactional
    public RecruitmentResponse close(Long postId, Long requesterId) {
        RecruitmentPost post = getPostAsLeader(postId, requesterId);
        post.close();
        return RecruitmentResponse.from(post);
    }

    @Transactional
    public RecruitmentResponse end(Long postId, Long requesterId) {
        RecruitmentPost post = getPostAsLeader(postId, requesterId);
        post.end();
        studyGroupProvisioningPort.endGroup(postId);
        return RecruitmentResponse.from(post);
    }
    
    @Transactional
    public RecruitmentResponse reopen(
            Long postId, Long requesterId, RecruitmentUpdateRequest request) {
        RecruitmentPost post = getPostAsLeader(postId, requesterId);
        if (post.getStatus() != RecruitmentStatus.CLOSED) { // 추가: CLOSED 상태가 아니면 재모집 불가
            throw new BusinessException(ErrorCode.RECRUITMENT_REOPEN_NOT_ALLOWED);
        }
        post.reopen(
                request.title(),
                request.category(),
                request.description(),
                request.goal(),
                request.method(),
                request.meetingType(),
                request.location(),
                request.onlineLink(),
                request.meetingDay(),
                request.capacity(),
                request.recruitmentDeadline(),
                request.expectedDuration(),
                request.conditions());
        return RecruitmentResponse.from(post);
    }

    private RecruitmentPost getPostAsLeader(Long postId, Long requesterId) {
        RecruitmentPost post =
                recruitmentRepository
                        .findById(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));
        if (!post.getLeader().getId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.RECRUITMENT_ACCESS_DENIED);
        }
        return post;
    }
}
