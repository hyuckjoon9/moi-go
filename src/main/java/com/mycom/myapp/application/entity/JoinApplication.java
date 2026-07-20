package com.mycom.myapp.application.entity;

import java.time.LocalDateTime;

import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.recruitment.entity.RecruitmentPost;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "join_applications",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_join_applications_post_applicant",
                    columnNames = {"post_id", "applicant_id"})
        })

public class JoinApplication { @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private RecruitmentPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Member applicant;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String motivation;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String experience;

    @Column(name = "available_time", length = 100)
    private String availableTime;

    @Column(name = "desired_role", length = 50)
    private String desiredRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private ApplicationStatus status;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Builder
    private JoinApplication(
            RecruitmentPost post,
            Member applicant,
            String motivation,
            String experience,
            String availableTime,
            String desiredRole) {
        this.post = post;
        this.applicant = applicant;
        this.motivation = motivation;
        this.experience = experience;
        this.availableTime = availableTime;
        this.desiredRole = desiredRole;
        this.status = ApplicationStatus.PENDING;
        this.appliedAt = LocalDateTime.now();
    }
    public void approve() {
        this.status = ApplicationStatus.APPROVED;
    }

    public void reject() {
        this.status = ApplicationStatus.REJECTED;
    }

    public void cancel() {
        this.status = ApplicationStatus.CANCELLED;
    }
  }
