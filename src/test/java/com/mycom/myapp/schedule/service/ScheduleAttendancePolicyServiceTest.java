package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScheduleAttendancePolicyServiceTest {

    private final StudyScheduleRepository scheduleRepository = mock(StudyScheduleRepository.class);
    private final GroupMemberRepository memberRepository = mock(GroupMemberRepository.class);
    private final ScheduleAttendancePolicyService service =
            new ScheduleAttendancePolicyService(scheduleRepository, memberRepository);

    @Test
    void returnsPolicyForActiveGroupMember() {
        StudyGroup group = group();
        StudySchedule schedule = schedule(group);
        when(scheduleRepository.findById(100L)).thenReturn(Optional.of(schedule));
        when(memberRepository.findByStudyGroupIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(GroupMember.join(group, 2L, GroupRole.MEMBER)));

        ScheduleAttendancePolicy policy = service.getAttendancePolicy(100L, 2L);

        assertThat(policy.scheduleId()).isEqualTo(100L);
        assertThat(policy.groupId()).isEqualTo(10L);
        assertThat(policy.activeGroupMember()).isTrue();
        assertThat(policy.effectiveDeadline()).isEqualTo(policy.scheduledAt());
    }

    @Test
    void rejectsMissingSchedule() {
        when(scheduleRepository.findById(100L)).thenReturn(Optional.empty());

        assertError(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    void rejectsMissingMembership() {
        StudyGroup group = group();
        when(scheduleRepository.findById(100L)).thenReturn(Optional.of(schedule(group)));
        when(memberRepository.findByStudyGroupIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertError(ErrorCode.GROUP_ACCESS_DENIED);
    }

    @Test
    void rejectsWithdrawnMembership() {
        StudyGroup group = group();
        GroupMember member = GroupMember.join(group, 2L, GroupRole.MEMBER);
        member.withdraw();
        when(scheduleRepository.findById(100L)).thenReturn(Optional.of(schedule(group)));
        when(memberRepository.findByStudyGroupIdAndUserId(10L, 2L)).thenReturn(Optional.of(member));

        assertError(ErrorCode.WITHDRAWN_GROUP_MEMBER);
    }

    private void assertError(ErrorCode errorCode) {
        assertThatThrownBy(() -> service.getAttendancePolicy(100L, 2L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private StudyGroup group() {
        StudyGroup group = StudyGroup.create(25L, "스터디");
        ReflectionTestUtils.setField(group, "id", 10L);
        return group;
    }

    private StudySchedule schedule(StudyGroup group) {
        StudySchedule schedule =
                StudySchedule.create(
                        group,
                        1L,
                        "일정",
                        LocalDateTime.of(2026, 7, 25, 19, 0),
                        null,
                        null,
                        null,
                        null,
                        null);
        ReflectionTestUtils.setField(schedule, "id", 100L);
        return schedule;
    }
}
