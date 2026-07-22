package com.mycom.myapp.activity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.activity.dto.request.ActivityRecordCreateRequest;
import com.mycom.myapp.activity.dto.request.ActivityReviewCreateRequest;
import com.mycom.myapp.activity.dto.response.ActivityRecordResponse;
import com.mycom.myapp.activity.dto.response.ActivityReviewResponse;
import com.mycom.myapp.activity.entity.ActivityRecord;
import com.mycom.myapp.activity.entity.ActivityReview;
import com.mycom.myapp.activity.service.ActivityService;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.entity.MemberRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ActivityControllerTest {

    private final ActivityService activityService = mock(ActivityService.class);
    private final ActivityController controller = new ActivityController(activityService);
    private final AuthenticatedMember authenticatedMember =
            new AuthenticatedMember(1L, "author@example.com", MemberRole.USER);

    @Test
    void createRecordReturnsCreatedWithAuthenticatedUserId() {
        ActivityRecordCreateRequest request =
                new ActivityRecordCreateRequest("토픽", "내용", null, null, null);
        ActivityRecord record =
                ActivityRecord.builder()
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("토픽")
                        .content("내용")
                        .build();
        when(activityService.createRecord(10L, 1L, request)).thenReturn(record);

        ResponseEntity<ActivityRecordResponse> response =
                controller.createRecord(10L, authenticatedMember, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getTopic()).isEqualTo("토픽");
        verify(activityService).createRecord(10L, 1L, request);
    }

    @Test
    void createRecordRejectsMissingAuthenticatedMember() {
        ActivityRecordCreateRequest request =
                new ActivityRecordCreateRequest("토픽", "내용", null, null, null);

        assertThatThrownBy(() -> controller.createRecord(10L, null, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void updateRecordReturnsOkWithAuthenticatedUserId() {
        ActivityRecordCreateRequest request =
                new ActivityRecordCreateRequest("수정된 토픽", "수정된 내용", null, null, null);
        ActivityRecord record =
                ActivityRecord.builder()
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("수정된 토픽")
                        .content("수정된 내용")
                        .build();
        when(activityService.updateRecord(10L, 1L, request)).thenReturn(record);

        ResponseEntity<ActivityRecordResponse> response =
                controller.updateRecord(10L, authenticatedMember, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTopic()).isEqualTo("수정된 토픽");
        verify(activityService).updateRecord(10L, 1L, request);
    }

    @Test
    void updateRecordRejectsMissingAuthenticatedMember() {
        ActivityRecordCreateRequest request =
                new ActivityRecordCreateRequest("토픽", "내용", null, null, null);

        assertThatThrownBy(() -> controller.updateRecord(10L, null, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void deleteRecordDeletesForAuthenticatedUserId() {
        ResponseEntity<Void> response = controller.deleteRecord(10L, authenticatedMember);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(activityService).deleteRecord(10L, 1L);
    }

    @Test
    void deleteRecordRejectsMissingAuthenticatedMember() {
        assertThatThrownBy(() -> controller.deleteRecord(10L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void getRecordReturnsServiceResult() {
        ActivityRecordResponse response =
                ActivityRecordResponse.builder()
                        .id(1L)
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("토픽")
                        .build();
        when(activityService.getRecordResponse(10L, 1L)).thenReturn(response);

        ResponseEntity<ActivityRecordResponse> result =
                controller.getRecord(10L, authenticatedMember);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void getRecordRejectsMissingAuthenticatedMember() {
        assertThatThrownBy(() -> controller.getRecord(10L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void createReviewReturnsCreatedWithAuthenticatedUserId() {
        ActivityReviewCreateRequest request = new ActivityReviewCreateRequest("좋았어요");
        ActivityReview review =
                ActivityReview.builder().activityRecordId(100L).userId(1L).comment("좋았어요").build();
        when(activityService.createReview(100L, 1L, request)).thenReturn(review);

        ResponseEntity<ActivityReviewResponse> response =
                controller.createReview(100L, authenticatedMember, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getComment()).isEqualTo("좋았어요");
        verify(activityService).createReview(100L, 1L, request);
    }

    @Test
    void createReviewRejectsMissingAuthenticatedMember() {
        ActivityReviewCreateRequest request = new ActivityReviewCreateRequest("좋았어요");

        assertThatThrownBy(() -> controller.createReview(100L, null, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void deleteReviewDeletesForAuthenticatedUserId() {
        ResponseEntity<Void> response = controller.deleteReview(100L, authenticatedMember);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(activityService).deleteReview(100L, 1L);
    }

    @Test
    void deleteReviewRejectsMissingAuthenticatedMember() {
        assertThatThrownBy(() -> controller.deleteReview(100L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void getReviewsReturnsServiceResult() {
        List<ActivityReviewResponse> reviews =
                List.of(ActivityReviewResponse.builder().id(1L).activityRecordId(100L).build());
        when(activityService.getReviews(100L, 1L)).thenReturn(reviews);

        ResponseEntity<List<ActivityReviewResponse>> response =
                controller.getReviews(100L, authenticatedMember);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(reviews);
    }

    @Test
    void getReviewsRejectsMissingAuthenticatedMember() {
        assertThatThrownBy(() -> controller.getReviews(100L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
