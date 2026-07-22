package com.mycom.myapp.activity.service;

import com.mycom.myapp.activity.dto.request.ActivityRecordCreateRequest;
import com.mycom.myapp.activity.dto.request.ActivityReviewCreateRequest;
import com.mycom.myapp.activity.dto.response.ActivityRecordResponse;
import com.mycom.myapp.activity.dto.response.ActivityReviewResponse;
import com.mycom.myapp.activity.entity.ActivityRecord;
import com.mycom.myapp.activity.entity.ActivityReview;
import com.mycom.myapp.activity.repository.ActivityRecordRepository;
import com.mycom.myapp.activity.repository.ActivityReviewRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 기록(ActivityRecord)과 그에 대한 리뷰(ActivityReview)를 함께 다루는 서비스. 기록은 일정당 최대 1건이며 해당 일정 그룹의 활성
 * LEADER/MANAGER만 등록·수정·삭제할 수 있고, 리뷰는 역할과 무관하게 활성 그룹원이면 누구나 남길 수 있다. 리뷰는 기록당 사용자 1인당 최대 1건이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRecordRepository activityRecordRepository;
    private final ActivityReviewRepository activityReviewRepository;
    private final StudyScheduleRepository studyScheduleRepository;
    private final GroupMemberRepository groupMemberRepository;

    /** 일정 그룹의 LEADER/MANAGER가 활동 기록을 처음 작성한다. 이미 기록이 있으면 거부한다. */
    @Transactional
    public ActivityRecord createRecord(
            Long scheduleId, Long authorId, ActivityRecordCreateRequest request) {
        validateManager(scheduleId, authorId);
        if (activityRecordRepository.findByScheduleId(scheduleId).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVITY_RECORD);
        }
        ActivityRecord record =
                ActivityRecord.builder()
                        .scheduleId(scheduleId)
                        .authorId(authorId)
                        .topic(request.getTopic())
                        .content(request.getContent())
                        .assignment(request.getAssignment())
                        .nextPreparation(request.getNextPreparation())
                        .referenceLinks(request.getReferenceLinks())
                        .build();
        return activityRecordRepository.save(record);
    }

    /** 일정 그룹의 LEADER/MANAGER가 활동 기록을 수정한다. */
    @Transactional
    public ActivityRecord updateRecord(
            Long scheduleId, Long requesterId, ActivityRecordCreateRequest request) {
        validateManager(scheduleId, requesterId);
        ActivityRecord record = getRecord(scheduleId);
        record.update(
                request.getTopic(),
                request.getContent(),
                request.getAssignment(),
                request.getNextPreparation(),
                request.getReferenceLinks());
        return record;
    }

    /** 일정 그룹의 LEADER/MANAGER가 활동 기록을 삭제한다. */
    @Transactional
    public void deleteRecord(Long scheduleId, Long requesterId) {
        validateManager(scheduleId, requesterId);
        ActivityRecord record = getRecord(scheduleId);
        activityRecordRepository.delete(record);
    }

    /** 일정 그룹의 활성 그룹원이 활동 기록을 조회한다. */
    public ActivityRecordResponse getRecordResponse(Long scheduleId, Long userId) {
        validateActiveMember(scheduleId, userId);
        return ActivityRecordResponse.of(getRecord(scheduleId));
    }

    /** 활동 기록이 속한 일정 그룹의 활성 그룹원이 리뷰를 작성한다. 이미 작성한 리뷰가 있으면 거부한다. */
    @Transactional
    public ActivityReview createReview(
            Long activityRecordId, Long userId, ActivityReviewCreateRequest request) {
        ActivityRecord record = getRecordById(activityRecordId);
        validateActiveMember(record.getScheduleId(), userId);
        if (activityReviewRepository
                .findByActivityRecordIdAndUserId(activityRecordId, userId)
                .isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVITY_REVIEW);
        }
        ActivityReview review =
                ActivityReview.builder()
                        .activityRecordId(activityRecordId)
                        .userId(userId)
                        .comment(request.getComment())
                        .build();
        return activityReviewRepository.save(review);
    }

    /** 활동 기록이 속한 일정 그룹의 활성 그룹원이 본인이 남긴 리뷰를 삭제한다. */
    @Transactional
    public void deleteReview(Long activityRecordId, Long userId) {
        ActivityRecord record = getRecordById(activityRecordId);
        validateActiveMember(record.getScheduleId(), userId);
        ActivityReview review =
                activityReviewRepository
                        .findByActivityRecordIdAndUserId(activityRecordId, userId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.ACTIVITY_REVIEW_NOT_FOUND));
        activityReviewRepository.delete(review);
    }

    /** 활동 기록이 속한 일정 그룹의 활성 그룹원이 리뷰 목록을 조회한다. */
    public List<ActivityReviewResponse> getReviews(Long activityRecordId, Long userId) {
        ActivityRecord record = getRecordById(activityRecordId);
        validateActiveMember(record.getScheduleId(), userId);
        return activityReviewRepository.findByActivityRecordId(activityRecordId).stream()
                .map(ActivityReviewResponse::of)
                .toList();
    }

    /** scheduleId의 활동 기록을 찾는다. 없으면 예외. */
    private ActivityRecord getRecord(Long scheduleId) {
        return activityRecordRepository
                .findByScheduleId(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_RECORD_NOT_FOUND));
    }

    /** activityRecordId의 활동 기록을 찾는다. 없으면 예외. */
    private ActivityRecord getRecordById(Long activityRecordId) {
        return activityRecordRepository
                .findById(activityRecordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_RECORD_NOT_FOUND));
    }

    /** requesterId가 scheduleId 소속 그룹의 활성 LEADER/MANAGER인지 검증한다. */
    private void validateManager(Long scheduleId, Long requesterId) {
        GroupMember member = validateActiveMember(scheduleId, requesterId);
        if (member.getRole() != GroupRole.LEADER && member.getRole() != GroupRole.MANAGER) {
            throw new BusinessException(ErrorCode.ACTIVITY_RECORD_ACCESS_DENIED);
        }
    }

    /** userId가 scheduleId 소속 그룹의 활성 그룹원인지(역할 무관) 검증한다. */
    private GroupMember validateActiveMember(Long scheduleId, Long userId) {
        StudySchedule schedule =
                studyScheduleRepository
                        .findById(scheduleId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        Long groupId = schedule.getStudyGroup().getId();
        GroupMember member =
                groupMemberRepository
                        .findByStudyGroupIdAndUserId(groupId, userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));
        if (member.getStatus() == GroupMemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_GROUP_MEMBER);
        }
        return member;
    }
}
