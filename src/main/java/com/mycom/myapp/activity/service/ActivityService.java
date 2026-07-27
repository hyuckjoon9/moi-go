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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRecordRepository activityRecordRepository;
    private final ActivityReviewRepository activityReviewRepository;
    private final StudyScheduleRepository studyScheduleRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final Clock clock;

    @Transactional
    public ActivityRecord createRecord(
            Long scheduleId, Long authorId, ActivityRecordCreateRequest request) {
        validateManager(scheduleId, authorId);
        validateScheduleStarted(scheduleId);
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

    @Transactional
    public void deleteRecord(Long scheduleId, Long requesterId) {
        validateManager(scheduleId, requesterId);
        ActivityRecord record = getRecord(scheduleId);
        activityRecordRepository.delete(record);
    }

    public ActivityRecordResponse getRecordResponse(Long scheduleId, Long userId) {
        validateActiveMember(scheduleId, userId);
        return ActivityRecordResponse.of(getRecord(scheduleId));
    }

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

    @Transactional
    public void deleteReviewByManager(Long activityRecordId, Long reviewId, Long requesterId) {
        ActivityRecord record = getRecordById(activityRecordId);
        validateManager(record.getScheduleId(), requesterId);
        ActivityReview review =
                activityReviewRepository
                        .findById(reviewId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.ACTIVITY_REVIEW_NOT_FOUND));
        if (!review.getActivityRecordId().equals(activityRecordId)) {
            throw new BusinessException(ErrorCode.ACTIVITY_REVIEW_NOT_FOUND);
        }
        activityReviewRepository.delete(review);
    }

    public List<ActivityReviewResponse> getReviews(Long activityRecordId, Long userId) {
        ActivityRecord record = getRecordById(activityRecordId);
        validateActiveMember(record.getScheduleId(), userId);
        return activityReviewRepository.findByActivityRecordId(activityRecordId).stream()
                .map(ActivityReviewResponse::of)
                .toList();
    }

    private ActivityRecord getRecord(Long scheduleId) {
        return activityRecordRepository
                .findByScheduleId(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_RECORD_NOT_FOUND));
    }

    private ActivityRecord getRecordById(Long activityRecordId) {
        return activityRecordRepository
                .findById(activityRecordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_RECORD_NOT_FOUND));
    }

    private void validateManager(Long scheduleId, Long requesterId) {
        GroupMember member = validateActiveMember(scheduleId, requesterId);
        if (member.getRole() != GroupRole.LEADER && member.getRole() != GroupRole.MANAGER) {
            throw new BusinessException(ErrorCode.ACTIVITY_RECORD_ACCESS_DENIED);
        }
    }

    private void validateScheduleStarted(Long scheduleId) {
        StudySchedule schedule =
                studyScheduleRepository
                        .findById(scheduleId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        if (LocalDateTime.now(clock).isBefore(schedule.getScheduledAt())) {
            throw new BusinessException(ErrorCode.ACTIVITY_RECORD_NOT_STARTED);
        }
    }

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
