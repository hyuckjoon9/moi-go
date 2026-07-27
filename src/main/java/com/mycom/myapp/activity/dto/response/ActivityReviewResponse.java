package com.mycom.myapp.activity.dto.response;

import com.mycom.myapp.activity.entity.ActivityReview;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ActivityReviewResponse {

    private Long id;
    private Long activityRecordId;
    private Long userId;
    private String comment;
    private LocalDateTime createdAt;

    public static ActivityReviewResponse of(ActivityReview review) {
        return ActivityReviewResponse.builder()
                .id(review.getId())
                .activityRecordId(review.getActivityRecordId())
                .userId(review.getUserId())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
