package com.mycom.myapp.admin.service;

import com.mycom.myapp.admin.dto.response.AdminRecruitmentDetailResponse;
import com.mycom.myapp.admin.dto.response.AdminRecruitmentListResponse;
import com.mycom.myapp.admin.entity.AdminAction;
import com.mycom.myapp.admin.entity.AdminAuditLog;
import com.mycom.myapp.admin.entity.AdminTargetType;
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

    public AdminRecruitmentListResponse getRecruitments(
            String keyword,
            com.mycom.myapp.recruitment.entity.RecruitmentStatus status,
            RecruitmentVisibility visibility,
            int page,
            int size) {
        return queryRepository.findRecruitments(keyword, status, visibility, page, size);
    }

    public AdminRecruitmentDetailResponse getRecruitment(Long recruitmentId) {
        AdminRecruitmentDetailResponse response = queryRepository.findRecruitment(recruitmentId);
        if (response == null) {
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
        return response;
    }

    @Transactional
    public AdminRecruitmentDetailResponse changeVisibility(
            Long adminId,
            Long recruitmentId,
            RecruitmentVisibility expectedVisibility,
            RecruitmentVisibility visibility,
            String reason) {
        AdminRecruitmentDetailResponse current = getRecruitment(recruitmentId);
        if (current.visibility() == visibility) {
            return current;
        }
        if (current.visibility() != expectedVisibility) {
            throw new BusinessException(ErrorCode.ADMIN_OPERATION_CONFLICT);
        }
        recruitmentAdministrationPort.changeVisibility(recruitmentId, visibility);
        auditLogRepository.save(
                AdminAuditLog.create(
                        adminId,
                        visibility == RecruitmentVisibility.HIDDEN
                                ? AdminAction.RECRUITMENT_HIDDEN
                                : AdminAction.RECRUITMENT_RESTORED,
                        AdminTargetType.RECRUITMENT,
                        recruitmentId,
                        current.title(),
                        snapshot(current.visibility()),
                        snapshot(visibility),
                        reason));
        return getRecruitment(recruitmentId);
    }

    private String snapshot(RecruitmentVisibility visibility) {
        return "{\"visibility\":\"" + visibility.name() + "\"}";
    }
}
