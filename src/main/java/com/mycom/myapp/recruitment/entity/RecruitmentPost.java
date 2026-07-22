package com.mycom.myapp.recruitment.entity;

import com.mycom.myapp.global.entity.BaseTimeEntity;
import com.mycom.myapp.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "recruitment_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitmentPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id", nullable = false)
    private Member leader;

    @Column(nullable = false)
    private String title;

    private String category;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String goal;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String method;

    private String meetingType;
    private String location;
    private String onlineLink;
    private String meetingDay;
    private Integer capacity;
    private LocalDate recruitmentDeadline;
    private String expectedDuration;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String conditions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecruitmentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public RecruitmentPost(
            Member leader,
            String title,
            String category,
            String description,
            String goal,
            String method,
            String meetingType,
            String location,
            String onlineLink,
            String meetingDay,
            Integer capacity,
            LocalDate recruitmentDeadline,
            String expectedDuration,
            String conditions,
            RecruitmentStatus status) {
        this.leader = leader;
        this.title = title;
        this.category = category;
        this.description = description;
        this.goal = goal;
        this.method = method;
        this.meetingType = meetingType;
        this.location = location;
        this.onlineLink = onlineLink;
        this.meetingDay = meetingDay;
        this.capacity = capacity;
        this.recruitmentDeadline = recruitmentDeadline;
        this.expectedDuration = expectedDuration;
        this.conditions = conditions;
        this.status = status;
    }

    public void update(
            String title,
            String category,
            String description,
            String goal,
            String method,
            String meetingType,
            String location,
            String onlineLink,
            String meetingDay,
            Integer capacity,
            LocalDate recruitmentDeadline,
            String expectedDuration,
            String conditions) {
        this.title = title;
        this.category = category;
        this.description = description;
        this.goal = goal;
        this.method = method;
        this.meetingType = meetingType;
        this.location = location;
        this.onlineLink = onlineLink;
        this.meetingDay = meetingDay;
        this.capacity = capacity;
        this.recruitmentDeadline = recruitmentDeadline;
        this.expectedDuration = expectedDuration;
        this.conditions = conditions;
    }

    public void close() {
        this.status = RecruitmentStatus.CLOSED;
    }

    public void activate() {
        this.status = RecruitmentStatus.ACTIVE;
    }

    public void end() {
        this.status = RecruitmentStatus.ENDED;
    }
}
