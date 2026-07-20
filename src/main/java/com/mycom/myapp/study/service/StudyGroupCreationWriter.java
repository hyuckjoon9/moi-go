package com.mycom.myapp.study.service;

import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StudyGroupCreationWriter {

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    StudyGroupCreationWriter(
            StudyGroupRepository studyGroupRepository,
            GroupMemberRepository groupMemberRepository) {
        this.studyGroupRepository = studyGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Transactional
    public Long create(CreateStudyGroupCommand command) {
        StudyGroup group =
                studyGroupRepository.saveAndFlush(
                        StudyGroup.create(command.postId(), command.groupName()));

        List<GroupMember> members = new ArrayList<>();
        members.add(GroupMember.join(group, command.leaderUserId(), GroupRole.LEADER));

        Set<Long> distinctApprovedUserIds = new LinkedHashSet<>(command.approvedUserIds());
        distinctApprovedUserIds.remove(command.leaderUserId());
        distinctApprovedUserIds.forEach(
                userId -> members.add(GroupMember.join(group, userId, GroupRole.MEMBER)));

        groupMemberRepository.saveAll(members);
        groupMemberRepository.flush();
        return group.getId();
    }
}
