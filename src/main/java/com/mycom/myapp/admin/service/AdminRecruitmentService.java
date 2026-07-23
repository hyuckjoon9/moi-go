package com.mycom.myapp.admin.service;

import com.mycom.myapp.admin.dto.response.AdminRecruitmentDetailResponse;
import com.mycom.myapp.admin.repository.AdminAuditLogRepository;
import com.mycom.myapp.admin.repository.AdminRecruitmentQueryRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;
import com.mycom.myapp.recruitment.service.port.RecruitmentAdministrationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminRecruitmentService {

    private final AdminRecruitmentQueryRepository queryRepository;
    private final RecruitmentAdministrationPort recruitmentAdministrationPort;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminRecruitmentService(
            AdminRecruitmentQueryRepository queryRepository,
            RecruitmentAdministrationPort recruitmentAdministrationPort,
            AdminAuditLogRepository auditLogRepository) {
        this.queryRepository = queryRepository;
        this.recruitmentAdministrationPort = recruitmentAdministrationPort;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AdminRecruitmentDetailResponse changeVisibility(
            Long adminId,
            Long recruitmentId,
            RecruitmentVisibility expectedVisibility,
            RecruitmentVisibility visibility,
            String reason) {
        AdminRecruitmentDetailResponse current = queryRepository.findRecruitment(recruitmentId);
        if (current == null) {
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
        if (current.visibility() == visibility) {
            return current;
        }
        if (current.visibility() != expectedVisibility) {
            throw new BusinessException(ErrorCode.ADMIN_OPERATION_CONFLICT);
        }
        recruitmentAdministrationPort.changeVisibility(recruitmentId, visibility);
        return queryRepository.findRecruitment(recruitmentId);
    }
}
