package com.mycom.myapp.schedule.service;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.schedule.service.port.ScheduleAttendancePolicyReader;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScheduleAttendancePolicyService implements ScheduleAttendancePolicyReader {

    private final StudyScheduleRepository scheduleRepository;
    private final GroupMemberRepository memberRepository;

    public ScheduleAttendancePolicyService(
            StudyScheduleRepository scheduleRepository, GroupMemberRepository memberRepository) {
        this.scheduleRepository = scheduleRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    public ScheduleAttendancePolicy getAttendancePolicy(Long scheduleId, Long userId) {
        StudySchedule schedule =
                scheduleRepository
                        .findById(scheduleId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        Long groupId = schedule.getStudyGroup().getId();
        GroupMember member =
                memberRepository
                        .findByStudyGroupIdAndUserId(groupId, userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));
        if (member.getStatus() == GroupMemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_GROUP_MEMBER);
        }
        return new ScheduleAttendancePolicy(
                schedule.getId(),
                groupId,
                schedule.getStudyGroup().getStatus(),
                true,
                schedule.getScheduledAt(),
                schedule.getResponseDeadline());
    }
}
