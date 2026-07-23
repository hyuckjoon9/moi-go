package com.mycom.myapp.study.service;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import com.mycom.myapp.study.dto.response.GroupMemberSummaryResponse;
import com.mycom.myapp.study.dto.response.MyStudyGroupResponse;
import com.mycom.myapp.study.dto.response.StudyGroupHomeResponse;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyGroupService {

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberRepository memberRepository;

    public StudyGroupService(
            StudyGroupRepository studyGroupRepository,
            GroupMemberRepository groupMemberRepository,
            MemberRepository memberRepository) {
        this.studyGroupRepository = studyGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.memberRepository = memberRepository;
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

        List<GroupMember> activeMembers =
                groupMemberRepository
                        .findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                                groupId, GroupMemberStatus.ACTIVE);
        Map<Long, Member> membersById = new HashMap<>();
        memberRepository
                .findAllById(activeMembers.stream().map(GroupMember::getUserId).toList())
                .forEach(member -> membersById.put(member.getId(), member));
        List<GroupMemberSummaryResponse> members =
                activeMembers.stream()
                        .map(
                                member -> {
                                    Member profile = membersById.get(member.getUserId());
                                    if (profile == null) {
                                        throw new IllegalStateException("그룹원의 회원 정보를 찾을 수 없습니다.");
                                    }
                                    return GroupMemberSummaryResponse.of(
                                            member,
                                            profile.getNickname(),
                                            profile.getProfileImageUrl());
                                })
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
