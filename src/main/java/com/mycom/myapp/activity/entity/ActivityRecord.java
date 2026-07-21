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
        name = "activity_records",
        uniqueConstraints = @UniqueConstraint(columnNames = "schedule_id"))
public class ActivityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId; // study_schedules.id (study_schedules 도메인 소유 FK), 일정당 활동 기록은 최대 1건

    @Column(name = "author_id", nullable = false)
    private Long authorId; // users.id (user 도메인 소유 FK)

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String assignment;

    @Column(name = "next_preparation", columnDefinition = "text")
    private String nextPreparation;

    @Column(name = "reference_links", columnDefinition = "text")
    private String referenceLinks;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public ActivityRecord(
            Long scheduleId,
            Long authorId,
            String topic,
            String content,
            String assignment,
            String nextPreparation,
            String referenceLinks) {
        if (scheduleId == null) {
            throw new IllegalArgumentException("일정 식별자는 필수입니다.");
        }
        this.scheduleId = scheduleId;
        this.authorId = authorId;
        this.topic = topic;
        this.content = content;
        this.assignment = assignment;
        this.nextPreparation = nextPreparation;
        this.referenceLinks = referenceLinks;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** 활동 기록 내용을 수정한다. */
    public void update(
            String topic,
            String content,
            String assignment,
            String nextPreparation,
            String referenceLinks) {
        this.topic = topic;
        this.content = content;
        this.assignment = assignment;
        this.nextPreparation = nextPreparation;
        this.referenceLinks = referenceLinks;
        this.updatedAt = LocalDateTime.now();
    }
}
