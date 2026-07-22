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
import com.mycom.myapp.study.service.port.StudyGroupProvisioningPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyGroupProvisioningService implements StudyGroupProvisioningPort {

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    StudyGroupProvisioningService(
            StudyGroupRepository studyGroupRepository,
            GroupMemberRepository groupMemberRepository) {
        this.studyGroupRepository = studyGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Long createGroup(CreateStudyGroupCommand command) {
        return studyGroupRepository
                .findByPostId(command.postId())
                .map(StudyGroup::getId)
                .orElseGet(() -> createNewOrReturnConcurrentGroup(command));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Long addMember(AddStudyGroupMemberCommand command) {
        StudyGroup group = getGroup(command.postId());
        if (group.getStatus() == GroupStatus.ENDED) {
            throw new BusinessException(ErrorCode.GROUP_ENDED);
        }
        return groupMemberRepository
                .findByStudyGroupIdAndUserId(group.getId(), command.userId())
                .map(member -> existingMemberId(group, member))
                .orElseGet(
                        () -> {
                            groupMemberRepository.saveAndFlush(
                                    GroupMember.join(group, command.userId(), GroupRole.MEMBER));
                            return group.getId();
                        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Long endGroup(Long postId) {
        StudyGroup group = getGroup(postId);
        if (group.getStatus() == GroupStatus.ACTIVE) {
            group.end();
        }
        return group.getId();
    }

    private Long createNewOrReturnConcurrentGroup(CreateStudyGroupCommand command) {
        try {
            StudyGroup group =
                    studyGroupRepository.saveAndFlush(
                            StudyGroup.create(command.postId(), command.groupName()));
            groupMemberRepository.saveAndFlush(
                    GroupMember.join(group, command.leaderUserId(), GroupRole.LEADER));
            return group.getId();
        } catch (DataIntegrityViolationException failure) {
            return studyGroupRepository
                    .findByPostId(command.postId())
                    .map(StudyGroup::getId)
                    .orElseThrow(() -> failure);
        }
    }

    private StudyGroup getGroup(Long postId) {
        return studyGroupRepository
                .findByPostId(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private Long existingMemberId(StudyGroup group, GroupMember member) {
        if (member.getStatus() == GroupMemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_GROUP_MEMBER);
        }
        return group.getId();
    }
}
