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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 기록(ActivityRecord)과 그에 대한 리뷰(ActivityReview)를 함께 다루는 서비스. 기록은 일정당 최대 1건이며 작성자만 수정·삭제할 수 있고,
 * 리뷰는 기록당 사용자 1인당 최대 1건이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRecordRepository activityRecordRepository;
    private final ActivityReviewRepository activityReviewRepository;

    /** 일정에 활동 기록을 처음 작성한다. 이미 기록이 있으면 거부한다. */
    @Transactional
    public ActivityRecord createRecord(
            Long scheduleId, Long authorId, ActivityRecordCreateRequest request) {
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

    /** 작성자 본인이 활동 기록을 수정한다. */
    @Transactional
    public ActivityRecord updateRecord(
            Long scheduleId, Long requesterId, ActivityRecordCreateRequest request) {
        ActivityRecord record = getRecord(scheduleId);
        requireAuthor(record, requesterId);
        record.update(
                request.getTopic(),
                request.getContent(),
                request.getAssignment(),
                request.getNextPreparation(),
                request.getReferenceLinks());
        return record;
    }

    /** 작성자 본인이 활동 기록을 삭제한다. */
    @Transactional
    public void deleteRecord(Long scheduleId, Long requesterId) {
        ActivityRecord record = getRecord(scheduleId);
        requireAuthor(record, requesterId);
        activityRecordRepository.delete(record);
    }

    /** 일정의 활동 기록을 조회한다. */
    public ActivityRecordResponse getRecordResponse(Long scheduleId) {
        return ActivityRecordResponse.of(getRecord(scheduleId));
    }

    /** 활동 기록에 리뷰를 작성한다. 이미 작성한 리뷰가 있으면 거부한다. */
    @Transactional
    public ActivityReview createReview(
            Long activityRecordId, Long userId, ActivityReviewCreateRequest request) {
        if (!activityRecordRepository.existsById(activityRecordId)) {
            throw new BusinessException(ErrorCode.ACTIVITY_RECORD_NOT_FOUND);
        }
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

    /** 작성자 본인이 남긴 리뷰를 삭제한다. */
    @Transactional
    public void deleteReview(Long activityRecordId, Long userId) {
        ActivityReview review =
                activityReviewRepository
                        .findByActivityRecordIdAndUserId(activityRecordId, userId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.ACTIVITY_REVIEW_NOT_FOUND));
        activityReviewRepository.delete(review);
    }

    /** 활동 기록에 달린 리뷰 목록을 조회한다. */
    public List<ActivityReviewResponse> getReviews(Long activityRecordId) {
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

    private void requireAuthor(ActivityRecord record, Long requesterId) {
        if (!record.getAuthorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.ACTIVITY_RECORD_ACCESS_DENIED);
        }
    }
}
