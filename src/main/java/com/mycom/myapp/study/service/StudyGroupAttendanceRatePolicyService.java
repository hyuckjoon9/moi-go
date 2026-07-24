package com.mycom.myapp.study.service;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.GroupStatus;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import com.mycom.myapp.study.service.port.StudyGroupAttendanceRatePolicyReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudyGroupAttendanceRatePolicyService implements StudyGroupAttendanceRatePolicyReader {

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    public StudyGroupAttendanceRatePolicyService(
            StudyGroupRepository studyGroupRepository,
            GroupMemberRepository groupMemberRepository) {
        this.studyGroupRepository = studyGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Override
    public StudyGroupAttendanceRatePolicy getAttendanceRatePolicy(Long groupId, Long requesterId) {
        StudyGroup group =
                studyGroupRepository
                        .findById(groupId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        GroupMember member =
                groupMemberRepository
                        .findByStudyGroupIdAndUserId(groupId, requesterId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));
        if (member.getStatus() == GroupMemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_GROUP_MEMBER);
        }

        boolean canViewAllAttendanceRates =
                group.getStatus() == GroupStatus.ACTIVE
                        && (member.getRole() == GroupRole.LEADER
                                || member.getRole() == GroupRole.MANAGER);
        return new StudyGroupAttendanceRatePolicy(groupId, canViewAllAttendanceRates);
    }
}
