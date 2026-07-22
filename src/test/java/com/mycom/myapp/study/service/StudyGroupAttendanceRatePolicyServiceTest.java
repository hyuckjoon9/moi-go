package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StudyGroupAttendanceRatePolicyServiceTest {

    private final StudyGroupRepository studyGroupRepository = mock(StudyGroupRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final StudyGroupAttendanceRatePolicyService service =
            new StudyGroupAttendanceRatePolicyService(studyGroupRepository, groupMemberRepository);

    @Test
    void allowsOnlyActiveLeaderToViewAllAttendanceRates() {
        StudyGroup group = group();
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByStudyGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(GroupMember.join(group, 1L, GroupRole.LEADER)));

        StudyGroupAttendanceRatePolicy policy = service.getAttendanceRatePolicy(10L, 1L);

        assertThat(policy.groupId()).isEqualTo(10L);
        assertThat(policy.canViewAllAttendanceRates()).isTrue();
    }

    @Test
    void allowsActiveManagerToViewAllAttendanceRates() {
        StudyGroup group = group();
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByStudyGroupIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(GroupMember.join(group, 2L, GroupRole.MANAGER)));

        assertThat(service.getAttendanceRatePolicy(10L, 2L).canViewAllAttendanceRates()).isTrue();
    }

    @Test
    void deniesActiveMemberFromViewingAllAttendanceRates() {
        StudyGroup group = group();
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByStudyGroupIdAndUserId(10L, 3L))
                .thenReturn(Optional.of(GroupMember.join(group, 3L, GroupRole.MEMBER)));

        assertThat(service.getAttendanceRatePolicy(10L, 3L).canViewAllAttendanceRates()).isFalse();
    }

    @Test
    void deniesLeaderWhenGroupHasEnded() {
        StudyGroup group = group();
        group.end();
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByStudyGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(GroupMember.join(group, 1L, GroupRole.LEADER)));

        assertThat(service.getAttendanceRatePolicy(10L, 1L).canViewAllAttendanceRates()).isFalse();
    }

    @Test
    void rejectsMissingMembership() {
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group()));
        when(groupMemberRepository.findByStudyGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.empty());

        assertError(ErrorCode.GROUP_ACCESS_DENIED);
    }

    @Test
    void rejectsMissingGroup() {
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.empty());

        assertError(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void rejectsWithdrawnMembership() {
        StudyGroup group = group();
        GroupMember member = GroupMember.join(group, 1L, GroupRole.LEADER);
        member.withdraw();
        when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByStudyGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(member));

        assertError(ErrorCode.WITHDRAWN_GROUP_MEMBER);
    }

    private void assertError(ErrorCode errorCode) {
        assertThatThrownBy(() -> service.getAttendanceRatePolicy(10L, 1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private StudyGroup group() {
        StudyGroup group = StudyGroup.create(25L, "스터디");
        ReflectionTestUtils.setField(group, "id", 10L);
        return group;
    }
}
