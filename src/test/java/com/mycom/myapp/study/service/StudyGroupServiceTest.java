package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.study.dto.response.StudyGroupHomeResponse;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.GroupStatus;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StudyGroupServiceTest {

    private final StudyGroupRepository studyGroupRepository = mock(StudyGroupRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final StudyGroupService service =
            new StudyGroupService(studyGroupRepository, groupMemberRepository);

    @Test
    void returnsGroupHomeForAnActiveMember() {
        StudyGroup group = group(10L, 25L);
        GroupMember currentMember = member(group, 2L, GroupRole.MEMBER, GroupMemberStatus.ACTIVE);
        GroupMember leader = member(group, 1L, GroupRole.LEADER, GroupMemberStatus.ACTIVE);
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByStudyGroupIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(currentMember));
        when(groupMemberRepository.findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                        10L, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(leader, currentMember));

        StudyGroupHomeResponse response = service.getHome(10L, 2L);

        assertThat(response.groupId()).isEqualTo(10L);
        assertThat(response.postId()).isEqualTo(25L);
        assertThat(response.myRole()).isEqualTo(GroupRole.MEMBER);
        assertThat(response.members())
                .extracting(member -> member.userId())
                .containsExactly(1L, 2L);
    }

    @Test
    void returnsEndedStatusForAnActiveMember() {
        StudyGroup group = group(10L, 25L);
        group.end();
        GroupMember currentMember = member(group, 2L, GroupRole.MEMBER, GroupMemberStatus.ACTIVE);
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByStudyGroupIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(currentMember));
        when(groupMemberRepository.findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                        10L, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(currentMember));

        assertThat(service.getHome(10L, 2L).status())
                .isEqualTo(com.mycom.myapp.study.entity.GroupStatus.ENDED);
    }

    @Test
    void throwsGroupNotFoundWhenGroupDoesNotExist() {
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.empty());

        assertError(ErrorCode.GROUP_NOT_FOUND, () -> service.getHome(10L, 2L));
    }

    @Test
    void throwsAccessDeniedWhenUserIsNotAMember() {
        StudyGroup group = group(10L, 25L);
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByStudyGroupIdAndUserId(10L, 2L))
                .thenReturn(Optional.empty());

        assertError(ErrorCode.GROUP_ACCESS_DENIED, () -> service.getHome(10L, 2L));
    }

    @Test
    void throwsWithdrawnMemberWhenMembershipIsWithdrawn() {
        StudyGroup group = group(10L, 25L);
        GroupMember withdrawn = member(group, 2L, GroupRole.MEMBER, GroupMemberStatus.WITHDRAWN);
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByStudyGroupIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(withdrawn));

        assertError(ErrorCode.WITHDRAWN_GROUP_MEMBER, () -> service.getHome(10L, 2L));
        verify(groupMemberRepository).findByStudyGroupIdAndUserId(10L, 2L);
    }

    @Test
    void returnsActiveAndEndedGroupsForUserInRepositoryOrder() {
        StudyGroup group = group(10L, 25L);
        StudyGroup endedGroup = group(11L, 26L);
        endedGroup.end();
        GroupMember member = member(group, 2L, GroupRole.LEADER, GroupMemberStatus.ACTIVE);
        GroupMember endedMember =
                member(endedGroup, 2L, GroupRole.MEMBER, GroupMemberStatus.ACTIVE);
        when(groupMemberRepository.findAllByUserIdAndStatusOrderByJoinedAtDescIdDesc(
                        2L, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(endedMember, member));

        assertThat(service.getMyGroups(2L))
                .extracting(response -> response.status())
                .containsExactly(GroupStatus.ENDED, GroupStatus.ACTIVE);
    }

    @Test
    void returnsEmptyMyGroupsWhenUserHasNoActiveMembership() {
        when(groupMemberRepository.findAllByUserIdAndStatusOrderByJoinedAtDescIdDesc(
                        2L, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(service.getMyGroups(2L)).isEmpty();
    }

    private StudyGroup group(Long id, Long postId) {
        StudyGroup group = StudyGroup.create(postId, "알고리즘 스터디");
        ReflectionTestUtils.setField(group, "id", id);
        ReflectionTestUtils.setField(group, "createdAt", LocalDateTime.of(2026, 7, 1, 10, 0));
        return group;
    }

    private GroupMember member(
            StudyGroup group, Long userId, GroupRole role, GroupMemberStatus expectedStatus) {
        GroupMember groupMember = GroupMember.join(group, userId, role);
        if (expectedStatus == GroupMemberStatus.WITHDRAWN) {
            groupMember.withdraw();
        }
        ReflectionTestUtils.setField(groupMember, "joinedAt", LocalDateTime.of(2026, 7, 1, 10, 0));
        return groupMember;
    }

    private void assertError(
            ErrorCode errorCode, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
