package com.mycom.myapp.activity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycom.myapp.activity.entity.ActivityReview;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ActivityReviewRepositoryTest {

    @Autowired private ActivityReviewRepository activityReviewRepository;

    @Test
    void findsReviewsByActivityRecordId() {
        ActivityReview first = save(100L, 20L);
        ActivityReview second = save(100L, 21L);
        save(101L, 20L);

        assertThat(activityReviewRepository.findByActivityRecordId(100L))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void findsReviewByActivityRecordIdAndUserId() {
        ActivityReview review = save(100L, 20L);

        assertThat(activityReviewRepository.findByActivityRecordIdAndUserId(100L, 20L))
                .contains(review);
        assertThat(activityReviewRepository.findByActivityRecordIdAndUserId(100L, 99L)).isEmpty();
    }

    @Test
    void rejectsDuplicateActivityRecordAndUser() {
        save(100L, 20L);

        assertThatThrownBy(
                        () ->
                                activityReviewRepository.saveAndFlush(
                                        ActivityReview.builder()
                                                .activityRecordId(100L)
                                                .userId(20L)
                                                .comment("또 남길래요")
                                                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ActivityReview save(Long activityRecordId, Long userId) {
        return activityReviewRepository.saveAndFlush(
                ActivityReview.builder()
                        .activityRecordId(activityRecordId)
                        .userId(userId)
                        .comment("좋았어요")
                        .build());
    }
}
