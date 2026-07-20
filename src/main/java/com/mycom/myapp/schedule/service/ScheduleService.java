package com.mycom.myapp.schedule.service;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.dto.request.ScheduleCreateRequest;
import com.mycom.myapp.schedule.dto.response.ScheduleResponse;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.GroupStatus;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

    private final StudyGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final StudyScheduleRepository scheduleRepository;
    private final Clock clock;

    public ScheduleService(
            StudyGroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            StudyScheduleRepository scheduleRepository,
            @Qualifier("scheduleClock") Clock clock) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.scheduleRepository = scheduleRepository;
        this.clock = clock;
    }

    @Transactional
    public ScheduleResponse create(Long groupId, Long memberId, ScheduleCreateRequest request) {
        StudyGroup group =
                groupRepository
                        .findById(groupId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        GroupMember member =
                memberRepository
                        .findByStudyGroupIdAndUserId(groupId, memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));
        if (member.getStatus() == GroupMemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_GROUP_MEMBER);
        }
        if (group.getStatus() == GroupStatus.ENDED) {
            throw new BusinessException(ErrorCode.GROUP_ENDED);
        }
        if (member.getRole() != GroupRole.LEADER && member.getRole() != GroupRole.MANAGER) {
            throw new BusinessException(ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime deadline = request.responseDeadline();
        if (!request.scheduledAt().isAfter(now)
                || (deadline != null
                        && (!deadline.isAfter(now) || deadline.isAfter(request.scheduledAt())))) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_TIME);
        }

        StudySchedule schedule =
                StudySchedule.create(
                        group,
                        memberId,
                        request.title(),
                        request.scheduledAt(),
                        request.location(),
                        request.onlineLink(),
                        request.content(),
                        request.materials(),
                        deadline);
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }
}
