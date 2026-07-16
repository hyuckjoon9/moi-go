package com.mycom.myapp.study.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "study_groups",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_study_groups_post_id", columnNames = "post_id"))
public class StudyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected StudyGroup() {}

    private StudyGroup(Long postId, String name) {
        if (postId == null) {
            throw new IllegalArgumentException("모집글 식별자는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("그룹 이름은 필수입니다.");
        }
        this.postId = postId;
        this.name = name.strip();
        this.status = GroupStatus.ACTIVE;
    }

    public static StudyGroup create(Long postId, String name) {
        return new StudyGroup(postId, name);
    }

    public void end() {
        status = GroupStatus.ENDED;
    }

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public String getName() {
        return name;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
