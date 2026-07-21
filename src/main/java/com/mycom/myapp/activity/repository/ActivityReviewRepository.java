package com.mycom.myapp.activity.repository;

import com.mycom.myapp.activity.entity.ActivityReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityReviewRepository extends JpaRepository<ActivityReview, Long> {

    List<ActivityReview> findByActivityRecordId(Long activityRecordId);

    Optional<ActivityReview> findByActivityRecordIdAndUserId(Long activityRecordId, Long userId);
}
