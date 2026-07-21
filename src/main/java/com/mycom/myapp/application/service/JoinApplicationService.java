package com.mycom.myapp.application.service;

import com.mycom.myapp.application.dto.request.JoinApplicationCreateRequest;
import com.mycom.myapp.application.dto.response.JoinApplicationResponse;
import com.mycom.myapp.application.dto.response.MyJoinApplicationResponse;
import com.mycom.myapp.application.entity.ApplicationStatus;
import com.mycom.myapp.application.entity.JoinApplication;
import com.mycom.myapp.application.repository.JoinApplicationRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentStatus;
import com.mycom.myapp.recruitment.repository.RecruitmentRepository;
import com.mycom.myapp.study.service.CreateStudyGroupCommand;
import com.mycom.myapp.study.service.StudyGroupCreationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JoinApplicationService {

    private final JoinApplicationRepository joinApplicationRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final MemberRepository memberRepository;
    private final StudyGroupCreationService studyGroupCreationService;

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

    public List<JoinApplicationResponse> getApplicants(Long postId, Long requesterId) {
        RecruitmentPost post =
                recruitmentRepository
                        .findById(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));

        if (!post.getLeader().getId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.APPLICATION_ACCESS_DENIED);
        }

        return joinApplicationRepository.findByPostId(postId).stream()
                .map(JoinApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MyJoinApplicationResponse> getMyApplications(
            Long applicantId, ApplicationStatus status) {
        List<JoinApplication> applications =
                status == null
                        ? joinApplicationRepository.findByApplicantId(applicantId)
                        : joinApplicationRepository.findByApplicantIdAndStatus(applicantId, status);
        return applications.stream().map(MyJoinApplicationResponse::from).toList();
    }

    @Transactional
    public JoinApplicationResponse approve(Long postId, Long applicationId, Long leaderId) {
        getPostAsLeader(postId, leaderId);
        JoinApplication application = getApplicationInPost(postId, applicationId);
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }
        application.approve();
        return JoinApplicationResponse.from(application);
    }

    @Transactional
    public JoinApplicationResponse reject(Long postId, Long applicationId, Long leaderId) {
        getPostAsLeader(postId, leaderId);
        JoinApplication application = getApplicationInPost(postId, applicationId);
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }
        application.reject();
        return JoinApplicationResponse.from(application);
    }

    @Transactional
    public Long confirmGroup(Long postId, Long leaderId) {
        RecruitmentPost post = getPostAsLeader(postId, leaderId);

        List<Long> approvedUserIds =
                joinApplicationRepository.findByPostId(postId).stream()
                        .filter(app -> app.getStatus() == ApplicationStatus.APPROVED)
                        .map(app -> app.getApplicant().getId())
                        .toList();

        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(postId, post.getTitle(), leaderId, approvedUserIds);
        return studyGroupCreationService.create(command);
    }

    private RecruitmentPost getPostAsLeader(Long postId, Long leaderId) {
        RecruitmentPost post =
                recruitmentRepository
                        .findById(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));
        if (!post.getLeader().getId().equals(leaderId)) {
            throw new BusinessException(ErrorCode.APPLICATION_ACCESS_DENIED);
        }
        return post;
    }

    private JoinApplication getApplicationInPost(Long postId, Long applicationId) {
        return joinApplicationRepository
                .findByIdAndPostId(applicationId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
    }
}
