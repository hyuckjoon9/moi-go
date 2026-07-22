package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.GroupStatus;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class StudyGroupProvisioningServiceTest {

    private final StudyGroupRepository groupRepository = mock(StudyGroupRepository.class);
    private final GroupMemberRepository memberRepository = mock(GroupMemberRepository.class);
    private final StudyGroupProvisioningService service =
            new StudyGroupProvisioningService(groupRepository, memberRepository);

    @Test
    void createsGroupWithLeaderOnly() {
        StudyGroup saved = mock(StudyGroup.class);
        when(saved.getId()).thenReturn(100L);
        when(groupRepository.findByPostId(10L)).thenReturn(Optional.empty());
        when(groupRepository.saveAndFlush(any(StudyGroup.class))).thenReturn(saved);

        assertThat(service.createGroup(new CreateStudyGroupCommand(10L, "그룹", 1L, List.of(2L))))
                .isEqualTo(100L);

        ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getRole()).isEqualTo(GroupRole.LEADER);
    }

    @Test
    void returnsExistingGroupWithoutWriting() {
        StudyGroup group = group(100L, GroupStatus.ACTIVE);
        when(groupRepository.findByPostId(10L)).thenReturn(Optional.of(group));

        assertThat(service.createGroup(new CreateStudyGroupCommand(10L, "그룹", 1L, List.of())))
                .isEqualTo(100L);
        verify(groupRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsConcurrentGroupAfterUniqueConflict() {
        StudyGroup group = group(100L, GroupStatus.ACTIVE);
        when(groupRepository.findByPostId(10L)).thenReturn(Optional.empty(), Optional.of(group));
        when(groupRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service.createGroup(new CreateStudyGroupCommand(10L, "그룹", 1L, List.of())))
                .isEqualTo(100L);
    }

    @Test
    void addsMemberWhenAbsent() {
        StudyGroup group = group(100L, GroupStatus.ACTIVE);
        when(groupRepository.findByPostId(10L)).thenReturn(Optional.of(group));
        when(memberRepository.findByStudyGroupIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        assertThat(service.addMember(new AddStudyGroupMemberCommand(10L, 2L))).isEqualTo(100L);
        verify(memberRepository).saveAndFlush(any(GroupMember.class));
    }

    @Test
    void returnsExistingIdForActiveMember() {
        StudyGroup group = group(100L, GroupStatus.ACTIVE);
        GroupMember member = member(group, GroupMemberStatus.ACTIVE);
        when(groupRepository.findByPostId(10L)).thenReturn(Optional.of(group));
        when(memberRepository.findByStudyGroupIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(member));

        assertThat(service.addMember(new AddStudyGroupMemberCommand(10L, 2L))).isEqualTo(100L);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingEndedAndWithdrawnStates() {
        when(groupRepository.findByPostId(10L)).thenReturn(Optional.empty());
        assertError(
                ErrorCode.GROUP_NOT_FOUND,
                () -> service.addMember(new AddStudyGroupMemberCommand(10L, 2L)));

        StudyGroup ended = group(100L, GroupStatus.ENDED);
        when(groupRepository.findByPostId(10L)).thenReturn(Optional.of(ended));
        assertError(
                ErrorCode.GROUP_ENDED,
                () -> service.addMember(new AddStudyGroupMemberCommand(10L, 2L)));

        StudyGroup active = group(100L, GroupStatus.ACTIVE);
        when(groupRepository.findByPostId(10L)).thenReturn(Optional.of(active));
        when(memberRepository.findByStudyGroupIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(member(active, GroupMemberStatus.WITHDRAWN)));
        assertError(
                ErrorCode.WITHDRAWN_GROUP_MEMBER,
                () -> service.addMember(new AddStudyGroupMemberCommand(10L, 2L)));
    }

    @Test
    void endsGroupIdempotentlyAndRejectsMissingGroup() {
        StudyGroup group = group(100L, GroupStatus.ACTIVE);
        when(groupRepository.findByPostId(10L)).thenReturn(Optional.of(group));
        assertThat(service.endGroup(10L)).isEqualTo(100L);
        verify(group).end();

        when(groupRepository.findByPostId(11L)).thenReturn(Optional.empty());
        assertError(ErrorCode.GROUP_NOT_FOUND, () -> service.endGroup(11L));
    }

    private StudyGroup group(Long id, GroupStatus status) {
        StudyGroup group = mock(StudyGroup.class);
        when(group.getId()).thenReturn(id);
        when(group.getStatus()).thenReturn(status);
        return group;
    }

    private GroupMember member(StudyGroup group, GroupMemberStatus status) {
        GroupMember member = GroupMember.join(group, 2L, GroupRole.MEMBER);
        if (status == GroupMemberStatus.WITHDRAWN) member.withdraw();
        return member;
    }

    private void assertError(ErrorCode code, ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
