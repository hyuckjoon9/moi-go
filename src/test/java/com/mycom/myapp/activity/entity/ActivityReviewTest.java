package com.mycom.myapp.activity.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivityReviewTest {

    @Test
    void builderInitializesFieldsAndCreatedAt() {
        ActivityReview review =
                ActivityReview.builder().activityRecordId(100L).userId(20L).comment("좋았어요").build();

        assertThat(review.getActivityRecordId()).isEqualTo(100L);
        assertThat(review.getUserId()).isEqualTo(20L);
        assertThat(review.getComment()).isEqualTo("좋았어요");
        assertThat(review.getCreatedAt()).isNotNull();
    }
}
