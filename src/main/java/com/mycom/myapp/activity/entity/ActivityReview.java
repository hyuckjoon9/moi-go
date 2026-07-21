package com.mycom.myapp.activity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "activity_reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = {"activity_record_id", "user_id"}))
public class ActivityReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_record_id", nullable = false)
    private Long activityRecordId; // activity_records.id, 활동 기록당 사용자 1인 1건

    @Column(name = "user_id", nullable = false)
    private Long userId; // users.id (user 도메인 소유 FK)

    @Column(nullable = false, length = 300)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public ActivityReview(Long activityRecordId, Long userId, String comment) {
        this.activityRecordId = activityRecordId;
        this.userId = userId;
        this.comment = comment;
        this.createdAt = LocalDateTime.now();
    }
}
