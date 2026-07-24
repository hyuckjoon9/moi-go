package com.mycom.myapp.admin.service;

import com.mycom.myapp.admin.dto.response.AdminMemberDetailResponse;
import com.mycom.myapp.admin.dto.response.AdminMemberListResponse;
import com.mycom.myapp.admin.entity.AdminAction;
import com.mycom.myapp.admin.entity.AdminAuditLog;
import com.mycom.myapp.admin.entity.AdminTargetType;
import com.mycom.myapp.admin.repository.AdminAuditLogRepository;
import com.mycom.myapp.admin.repository.AdminMemberQueryRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.member.entity.MemberStatus;
import com.mycom.myapp.member.service.port.MemberAdministrationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminMemberService {

    private final AdminMemberQueryRepository queryRepository;
    private final MemberAdministrationPort memberAdministrationPort;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminMemberService(
            AdminMemberQueryRepository queryRepository,
            MemberAdministrationPort memberAdministrationPort,
            AdminAuditLogRepository auditLogRepository) {
        this.queryRepository = queryRepository;
        this.memberAdministrationPort = memberAdministrationPort;
        this.auditLogRepository = auditLogRepository;
    }

    public AdminMemberListResponse getMembers(
            String keyword,
            com.mycom.myapp.member.entity.MemberRole role,
            MemberStatus status,
            int page,
            int size) {
        return queryRepository.findMembers(keyword, role, status, page, size);
    }

    public AdminMemberDetailResponse getMember(Long memberId) {
        AdminMemberDetailResponse response = queryRepository.findMember(memberId);
        if (response == null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return response;
    }

    @Transactional
    public AdminMemberDetailResponse changeStatus(
            Long adminId,
            Long memberId,
            MemberStatus expectedStatus,
            MemberStatus status,
            String reason) {
        AdminMemberDetailResponse current = getMember(memberId);
        if (current.status() == status) {
            return current;
        }
        if (current.status() != expectedStatus) {
            throw new BusinessException(ErrorCode.ADMIN_OPERATION_CONFLICT);
        }
        memberAdministrationPort.changeStatus(adminId, memberId, status);
        auditLogRepository.save(
                AdminAuditLog.create(
                        adminId,
                        AdminAction.MEMBER_STATUS_CHANGED,
                        AdminTargetType.MEMBER,
                        memberId,
                        current.nickname(),
                        snapshot(current.status()),
                        snapshot(status),
                        reason));
        return getMember(memberId);
    }

    private String snapshot(MemberStatus status) {
        return "{\"status\":\"" + status.name() + "\"}";
    }
}
