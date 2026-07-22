package com.mycom.myapp.study.service;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.study.dto.response.GroupMemberSummaryResponse;
import com.mycom.myapp.study.dto.response.MyStudyGroupResponse;
import com.mycom.myapp.study.dto.response.StudyGroupHomeResponse;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyGroupService {

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    public StudyGroupService(
            StudyGroupRepository studyGroupRepository,
            GroupMemberRepository groupMemberRepository) {
        this.studyGroupRepository = studyGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Transactional(readOnly = true)
    public StudyGroupHomeResponse getHome(Long groupId, Long userId) {
        StudyGroup studyGroup =
                studyGroupRepository
                        .findById(groupId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        GroupMember currentMember =
                groupMemberRepository
                        .findByStudyGroupIdAndUserId(groupId, userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));
        if (currentMember.getStatus() == GroupMemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_GROUP_MEMBER);
        }

        List<GroupMemberSummaryResponse> members =
                groupMemberRepository
                        .findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                                groupId, GroupMemberStatus.ACTIVE)
                        .stream()
                        .map(GroupMemberSummaryResponse::from)
                        .toList();
        return StudyGroupHomeResponse.of(studyGroup, currentMember.getRole(), members);
    }

    @Transactional(readOnly = true)
    public List<MyStudyGroupResponse> getMyGroups(Long userId) {
        return groupMemberRepository
                .findAllByUserIdAndStatusOrderByJoinedAtDescIdDesc(userId, GroupMemberStatus.ACTIVE)
                .stream()
                .map(MyStudyGroupResponse::from)
                .toList();
    }
}
