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
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private ActivityRecordRepository activityRecordRepository;
    @Mock private ActivityReviewRepository activityReviewRepository;
    @Mock private StudyScheduleRepository studyScheduleRepository;
    @Mock private GroupMemberRepository groupMemberRepository;

    @InjectMocks private ActivityService activityService;

    @Test
    void createRecordSavesNewRecord() {
        stubManager(10L, 1L, GroupRole.LEADER);
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
        stubManager(10L, 1L, GroupRole.MANAGER);
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
    void createRecordThrowsWhenScheduleNotFound() {
        given(studyScheduleRepository.findById(10L)).willReturn(Optional.empty());

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
                                        .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    @Test
    void createRecordThrowsWhenNotGroupMember() {
        StudyGroup group = group();
        given(studyScheduleRepository.findById(10L)).willReturn(Optional.of(schedule(group, 10L)));
        given(groupMemberRepository.findByStudyGroupIdAndUserId(100L, 1L))
                .willReturn(Optional.empty());

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
                                        .isEqualTo(ErrorCode.GROUP_ACCESS_DENIED));
    }

    @Test
    void createRecordThrowsWhenMemberIsWithdrawn() {
        StudyGroup group = group();
        GroupMember member = GroupMember.join(group, 1L, GroupRole.LEADER);
        member.withdraw();
        given(studyScheduleRepository.findById(10L)).willReturn(Optional.of(schedule(group, 10L)));
        given(groupMemberRepository.findByStudyGroupIdAndUserId(100L, 1L))
                .willReturn(Optional.of(member));

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
                                        .isEqualTo(ErrorCode.WITHDRAWN_GROUP_MEMBER));
    }

    @Test
    void createRecordThrowsWhenRequesterIsPlainMember() {
        stubManager(10L, 1L, GroupRole.MEMBER);

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
                                        .isEqualTo(ErrorCode.ACTIVITY_RECORD_ACCESS_DENIED));
    }

    @Test
    void updateRecordUpdatesExistingRecordWhenRequesterIsManager() {
        stubManager(10L, 2L, GroupRole.MANAGER);
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
                        2L,
                        new ActivityRecordCreateRequest("수정된 토픽", "수정된 내용", null, null, null));

        assertThat(result.getTopic()).isEqualTo("수정된 토픽");
        assertThat(result.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    void updateRecordThrowsWhenRecordNotFound() {
        stubManager(10L, 1L, GroupRole.LEADER);
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
    void updateRecordThrowsWhenRequesterIsPlainMember() {
        stubManager(10L, 2L, GroupRole.MEMBER);

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
    void deleteRecordDeletesExistingRecordWhenRequesterIsManager() {
        stubManager(10L, 1L, GroupRole.LEADER);
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
    void deleteRecordThrowsWhenRequesterIsPlainMember() {
        stubManager(10L, 2L, GroupRole.MEMBER);

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
        stubActivityRecordWithSchedule(100L, 10L);
        stubManager(10L, 20L, GroupRole.MEMBER);
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
        given(activityRecordRepository.findById(100L)).willReturn(Optional.empty());

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
    void createReviewThrowsWhenNotGroupMember() {
        stubActivityRecordWithSchedule(100L, 10L);
        StudyGroup group = group();
        given(studyScheduleRepository.findById(10L)).willReturn(Optional.of(schedule(group, 10L)));
        given(groupMemberRepository.findByStudyGroupIdAndUserId(100L, 20L))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                activityService.createReview(
                                        100L, 20L, new ActivityReviewCreateRequest("좋았어요")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.GROUP_ACCESS_DENIED));
    }

    @Test
    void createReviewThrowsWhenMemberIsWithdrawn() {
        stubActivityRecordWithSchedule(100L, 10L);
        StudyGroup group = group();
        GroupMember member = GroupMember.join(group, 20L, GroupRole.MEMBER);
        member.withdraw();
        given(studyScheduleRepository.findById(10L)).willReturn(Optional.of(schedule(group, 10L)));
        given(groupMemberRepository.findByStudyGroupIdAndUserId(100L, 20L))
                .willReturn(Optional.of(member));

        assertThatThrownBy(
                        () ->
                                activityService.createReview(
                                        100L, 20L, new ActivityReviewCreateRequest("좋았어요")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.WITHDRAWN_GROUP_MEMBER));
    }

    @Test
    void createReviewRejectsDuplicateReview() {
        stubActivityRecordWithSchedule(100L, 10L);
        stubManager(10L, 20L, GroupRole.MEMBER);
        ActivityReview existing =
                ActivityReview.builder().activityRecordId(100L).userId(20L).comment("좋았어요").build();
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
        stubActivityRecordWithSchedule(100L, 10L);
        stubManager(10L, 20L, GroupRole.MEMBER);
        ActivityReview existing =
                ActivityReview.builder().activityRecordId(100L).userId(20L).comment("좋았어요").build();
        given(activityReviewRepository.findByActivityRecordIdAndUserId(100L, 20L))
                .willReturn(Optional.of(existing));

        activityService.deleteReview(100L, 20L);

        verify(activityReviewRepository).delete(existing);
    }

    @Test
    void deleteReviewThrowsWhenRecordNotFound() {
        given(activityRecordRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.deleteReview(100L, 20L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ACTIVITY_RECORD_NOT_FOUND));
    }

    @Test
    void deleteReviewThrowsWhenReviewNotFound() {
        stubActivityRecordWithSchedule(100L, 10L);
        stubManager(10L, 20L, GroupRole.MEMBER);
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

    private void stubManager(Long scheduleId, Long userId, GroupRole role) {
        StudyGroup group = group();
        given(studyScheduleRepository.findById(scheduleId))
                .willReturn(Optional.of(schedule(group, scheduleId)));
        given(groupMemberRepository.findByStudyGroupIdAndUserId(100L, userId))
                .willReturn(Optional.of(GroupMember.join(group, userId, role)));
    }

    private void stubActivityRecordWithSchedule(Long activityRecordId, Long scheduleId) {
        ActivityRecord record =
                ActivityRecord.builder()
                        .scheduleId(scheduleId)
                        .authorId(1L)
                        .topic("토픽")
                        .content("내용")
                        .build();
        given(activityRecordRepository.findById(activityRecordId)).willReturn(Optional.of(record));
    }

    private StudyGroup group() {
        StudyGroup group = StudyGroup.create(25L, "스터디");
        ReflectionTestUtils.setField(group, "id", 100L);
        return group;
    }

    private StudySchedule schedule(StudyGroup group, Long scheduleId) {
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
        ReflectionTestUtils.setField(schedule, "id", scheduleId);
        return schedule;
    }
}
