package com.mycom.myapp.recruitment.service;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import com.mycom.myapp.recruitment.repository.RecruitmentRepository;
import com.mycom.myapp.recruitment.service.port.RecruitmentAdministrationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecruitmentAdministrationService implements RecruitmentAdministrationPort {

    private final RecruitmentRepository recruitmentRepository;

    public RecruitmentAdministrationService(RecruitmentRepository recruitmentRepository) {
        this.recruitmentRepository = recruitmentRepository;
    }

    @Override
    @Transactional
    public RecruitmentPost changeVisibility(Long recruitmentId, RecruitmentVisibility visibility) {
        RecruitmentPost post =
                recruitmentRepository
                        .findById(recruitmentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));
        post.changeVisibility(visibility);
        return post;
    }
}
