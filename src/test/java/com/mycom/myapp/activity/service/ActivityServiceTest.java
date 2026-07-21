package com.mycom.myapp.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mycom.myapp.activity.dto.request.ActivityRecordCreateRequest;
import com.mycom.myapp.activity.dto.request.ActivityReviewCreateRequest;
import com.mycom.myapp.activity.dto.response.ActivityReviewResponse;
import com.mycom.myapp.activity.entity.ActivityRecord;
import com.mycom.myapp.activity.entity.ActivityReview;
import com.mycom.myapp.activity.repository.ActivityRecordRepository;
import com.mycom.myapp.activity.repository.ActivityReviewRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private ActivityRecordRepository activityRecordRepository;
    @Mock private ActivityReviewRepository activityReviewRepository;

    @InjectMocks private ActivityService activityService;

    @Test
    void createRecordSavesNewRecord() {
        given(activityRecordRepository.findByScheduleId(10L)).willReturn(Optional.empty());
        given(activityRecordRepository.save(any(ActivityRecord.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ActivityRecord result =
                activityService.createRecord(
                        10L,
                        1L,
                        new ActivityRecordCreateRequest("토픽", "내용", "과제", "다음 준비물", "참고 링크"));

        assertThat(result.getScheduleId()).isEqualTo(10L);
        assertThat(result.getAuthorId()).isEqualTo(1L);
        assertThat(result.getTopic()).isEqualTo("토픽");
    }

    @Test
    void createRecordRejectsDuplicateRecord() {
        ActivityRecord existing =
                ActivityRecord.builder()
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("토픽")
                        .content("내용")
                        .build();
        given(activityRecordRepository.findByScheduleId(10L)).willReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                activityService.createRecord(
                                        10L,
                                        1L,
                                        new ActivityRecordCreateRequest(
                                                "토픽", "내용", null, null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_ACTIVITY_RECORD));
    }

    @Test
    void updateRecordUpdatesExistingRecordWhenRequesterIsAuthor() {
        ActivityRecord existing =
                ActivityRecord.builder()
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("토픽")
                        .content("내용")
                        .build();
        given(activityRecordRepository.findByScheduleId(10L)).willReturn(Optional.of(existing));

        ActivityRecord result =
                activityService.updateRecord(
                        10L,
                        1L,
                        new ActivityRecordCreateRequest("수정된 토픽", "수정된 내용", null, null, null));

        assertThat(result.getTopic()).isEqualTo("수정된 토픽");
        assertThat(result.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    void updateRecordThrowsWhenRecordNotFound() {
        given(activityRecordRepository.findByScheduleId(10L)).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                activityService.updateRecord(
                                        10L,
                                        1L,
                                        new ActivityRecordCreateRequest(
                                                "토픽", "내용", null, null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ACTIVITY_RECORD_NOT_FOUND));
    }

    @Test
    void updateRecordThrowsWhenRequesterIsNotAuthor() {
        ActivityRecord existing =
                ActivityRecord.builder()
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("토픽")
                        .content("내용")
                        .build();
        given(activityRecordRepository.findByScheduleId(10L)).willReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                activityService.updateRecord(
                                        10L,
                                        2L,
                                        new ActivityRecordCreateRequest(
                                                "토픽", "내용", null, null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ACTIVITY_RECORD_ACCESS_DENIED));
    }

    @Test
    void deleteRecordDeletesExistingRecordWhenRequesterIsAuthor() {
        ActivityRecord existing =
                ActivityRecord.builder()
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("토픽")
                        .content("내용")
                        .build();
        given(activityRecordRepository.findByScheduleId(10L)).willReturn(Optional.of(existing));

        activityService.deleteRecord(10L, 1L);

        verify(activityRecordRepository).delete(existing);
    }

    @Test
    void deleteRecordThrowsWhenRequesterIsNotAuthor() {
        ActivityRecord existing =
                ActivityRecord.builder()
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("토픽")
                        .content("내용")
                        .build();
        given(activityRecordRepository.findByScheduleId(10L)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> activityService.deleteRecord(10L, 2L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ACTIVITY_RECORD_ACCESS_DENIED));
    }

    @Test
    void getRecordResponseReturnsMappedResponse() {
        ActivityRecord existing =
                ActivityRecord.builder()
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("토픽")
                        .content("내용")
                        .build();
        given(activityRecordRepository.findByScheduleId(10L)).willReturn(Optional.of(existing));

        var response = activityService.getRecordResponse(10L);

        assertThat(response.getScheduleId()).isEqualTo(10L);
        assertThat(response.getTopic()).isEqualTo("토픽");
    }

    @Test
    void createReviewSavesNewReview() {
        given(activityRecordRepository.existsById(100L)).willReturn(true);
        given(activityReviewRepository.findByActivityRecordIdAndUserId(100L, 20L))
                .willReturn(Optional.empty());
        given(activityReviewRepository.save(any(ActivityReview.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ActivityReview result =
                activityService.createReview(100L, 20L, new ActivityReviewCreateRequest("좋았어요"));

        assertThat(result.getActivityRecordId()).isEqualTo(100L);
        assertThat(result.getUserId()).isEqualTo(20L);
        assertThat(result.getComment()).isEqualTo("좋았어요");
    }

    @Test
    void createReviewThrowsWhenRecordNotFound() {
        given(activityRecordRepository.existsById(100L)).willReturn(false);

        assertThatThrownBy(
                        () ->
                                activityService.createReview(
                                        100L, 20L, new ActivityReviewCreateRequest("좋았어요")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ACTIVITY_RECORD_NOT_FOUND));
    }

    @Test
    void createReviewRejectsDuplicateReview() {
        ActivityReview existing =
                ActivityReview.builder().activityRecordId(100L).userId(20L).comment("좋았어요").build();
        given(activityRecordRepository.existsById(100L)).willReturn(true);
        given(activityReviewRepository.findByActivityRecordIdAndUserId(100L, 20L))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                activityService.createReview(
                                        100L, 20L, new ActivityReviewCreateRequest("또 남길래요")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_ACTIVITY_REVIEW));
    }

    @Test
    void deleteReviewDeletesExistingReview() {
        ActivityReview existing =
                ActivityReview.builder().activityRecordId(100L).userId(20L).comment("좋았어요").build();
        given(activityReviewRepository.findByActivityRecordIdAndUserId(100L, 20L))
                .willReturn(Optional.of(existing));

        activityService.deleteReview(100L, 20L);

        verify(activityReviewRepository).delete(existing);
    }

    @Test
    void deleteReviewThrowsWhenReviewNotFound() {
        given(activityReviewRepository.findByActivityRecordIdAndUserId(100L, 20L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.deleteReview(100L, 20L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ACTIVITY_REVIEW_NOT_FOUND));
    }

    @Test
    void getReviewsThrowsWhenRecordNotFound() {
        given(activityRecordRepository.existsById(100L)).willReturn(false);

        assertThatThrownBy(() -> activityService.getReviews(100L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ACTIVITY_RECORD_NOT_FOUND));
    }

    @Test
    void getReviewsReturnsMappedResponses() {
        List<ActivityReview> reviews =
                List.of(
                        ActivityReview.builder()
                                .activityRecordId(100L)
                                .userId(20L)
                                .comment("좋았어요")
                                .build(),
                        ActivityReview.builder()
                                .activityRecordId(100L)
                                .userId(21L)
                                .comment("유익했습니다")
                                .build());
        given(activityRecordRepository.existsById(100L)).willReturn(true);
        given(activityReviewRepository.findByActivityRecordId(100L)).willReturn(reviews);

        List<ActivityReviewResponse> result = activityService.getReviews(100L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getComment()).isEqualTo("좋았어요");
        assertThat(result.get(1).getComment()).isEqualTo("유익했습니다");
    }
}
