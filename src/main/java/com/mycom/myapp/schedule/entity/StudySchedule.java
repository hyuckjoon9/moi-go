package com.mycom.myapp.schedule.entity;

import com.mycom.myapp.study.entity.StudyGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "study_schedules",
        indexes = {
            @Index(
                    name = "idx_study_schedules_group_scheduled",
                    columnList = "group_id,scheduled_at"),
            @Index(name = "idx_study_schedules_creator", columnList = "creator_id")
        })
public class StudySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private StudyGroup studyGroup;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(length = 255)
    private String location;

    @Column(name = "online_link", length = 500)
    private String onlineLink;

    @Column(columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String materials;

    @Column(name = "response_deadline")
    private LocalDateTime responseDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected StudySchedule() {}

    private StudySchedule(
            StudyGroup studyGroup,
            Long creatorId,
            String title,
            LocalDateTime scheduledAt,
            String location,
            String onlineLink,
            String content,
            String materials,
            LocalDateTime responseDeadline) {
        this.studyGroup = studyGroup;
        this.creatorId = creatorId;
        this.title = title;
        this.scheduledAt = scheduledAt;
        this.location = location;
        this.onlineLink = onlineLink;
        this.content = content;
        this.materials = materials;
        this.responseDeadline = responseDeadline;
    }

    public static StudySchedule create(
            StudyGroup studyGroup,
            Long creatorId,
            String title,
            LocalDateTime scheduledAt,
            String location,
            String onlineLink,
            String content,
            String materials,
            LocalDateTime responseDeadline) {
        if (studyGroup == null
                || creatorId == null
                || title == null
                || title.isBlank()
                || scheduledAt == null) {
            throw new IllegalArgumentException("일정 필수값이 누락되었습니다.");
        }
        if (responseDeadline != null && responseDeadline.isAfter(scheduledAt)) {
            throw new IllegalArgumentException("응답 마감 시간은 일정 시간보다 늦을 수 없습니다.");
        }
        return new StudySchedule(
                studyGroup,
                creatorId,
                title,
                scheduledAt,
                location,
                onlineLink,
                content,
                materials,
                responseDeadline);
    }

    @PrePersist
    private void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public Long getId() {
        return id;
    }

    public StudyGroup getStudyGroup() {
        return studyGroup;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public String getLocation() {
        return location;
    }

    public String getOnlineLink() {
        return onlineLink;
    }

    public String getContent() {
        return content;
    }

    public String getMaterials() {
        return materials;
    }

    public LocalDateTime getResponseDeadline() {
        return responseDeadline;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
