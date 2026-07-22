package com.mycom.myapp.activity.controller;

import com.mycom.myapp.activity.dto.request.ActivityRecordCreateRequest;
import com.mycom.myapp.activity.dto.request.ActivityReviewCreateRequest;
import com.mycom.myapp.activity.dto.response.ActivityRecordResponse;
import com.mycom.myapp.activity.dto.response.ActivityReviewResponse;
import com.mycom.myapp.activity.service.ActivityService;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.security.AuthenticatedMember;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /** 활동 기록 등록 */
    @PostMapping("/schedules/{scheduleId}/record")
    public ResponseEntity<ActivityRecordResponse> createRecord(
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody ActivityRecordCreateRequest request) {
        Long authorId = requireAuthenticatedId(authenticatedMember);
        ActivityRecordResponse response =
                ActivityRecordResponse.of(
                        activityService.createRecord(scheduleId, authorId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 활동 기록 수정 (작성자 본인) */
    @PutMapping("/schedules/{scheduleId}/record")
    public ResponseEntity<ActivityRecordResponse> updateRecord(
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody ActivityRecordCreateRequest request) {
        Long requesterId = requireAuthenticatedId(authenticatedMember);
        ActivityRecordResponse response =
                ActivityRecordResponse.of(
                        activityService.updateRecord(scheduleId, requesterId, request));
        return ResponseEntity.ok(response);
    }

    /** 활동 기록 삭제 (작성자 본인) */
    @DeleteMapping("/schedules/{scheduleId}/record")
    public ResponseEntity<Void> deleteRecord(
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        Long requesterId = requireAuthenticatedId(authenticatedMember);
        activityService.deleteRecord(scheduleId, requesterId);
        return ResponseEntity.noContent().build();
    }

    /** 활동 기록 조회 */
    @GetMapping("/schedules/{scheduleId}/record")
    public ResponseEntity<ActivityRecordResponse> getRecord(
            @PathVariable("scheduleId") Long scheduleId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        Long userId = requireAuthenticatedId(authenticatedMember);
        return ResponseEntity.ok(activityService.getRecordResponse(scheduleId, userId));
    }

    /** 활동 기록 리뷰 작성 */
    @PostMapping("/records/{activityRecordId}/reviews")
    public ResponseEntity<ActivityReviewResponse> createReview(
            @PathVariable("activityRecordId") Long activityRecordId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody ActivityReviewCreateRequest request) {
        Long userId = requireAuthenticatedId(authenticatedMember);
        ActivityReviewResponse response =
                ActivityReviewResponse.of(
                        activityService.createReview(activityRecordId, userId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 활동 기록 리뷰 삭제 (작성자 본인) */
    @DeleteMapping("/records/{activityRecordId}/reviews")
    public ResponseEntity<Void> deleteReview(
            @PathVariable("activityRecordId") Long activityRecordId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        Long userId = requireAuthenticatedId(authenticatedMember);
        activityService.deleteReview(activityRecordId, userId);
        return ResponseEntity.noContent().build();
    }

    /** 활동 기록 리뷰 목록 조회 */
    @GetMapping("/records/{activityRecordId}/reviews")
    public ResponseEntity<List<ActivityReviewResponse>> getReviews(
            @PathVariable("activityRecordId") Long activityRecordId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        Long userId = requireAuthenticatedId(authenticatedMember);
        return ResponseEntity.ok(activityService.getReviews(activityRecordId, userId));
    }

    private Long requireAuthenticatedId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authenticatedMember.id();
    }
}
