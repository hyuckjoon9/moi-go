package com.mycom.myapp.study.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "group_members",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_group_members_group_user",
                        columnNames = {"group_id", "user_id"}),
        indexes = @Index(name = "idx_group_members_user_status", columnList = "user_id,status"))
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private StudyGroup studyGroup;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private GroupRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private GroupMemberStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    protected GroupMember() {}

    private GroupMember(StudyGroup studyGroup, Long userId, GroupRole role) {
        validateJoinArguments(studyGroup, userId, role);
        this.studyGroup = studyGroup;
        this.userId = userId;
        this.role = role;
        this.status = GroupMemberStatus.ACTIVE;
    }

    public static GroupMember join(StudyGroup studyGroup, Long userId, GroupRole role) {
        return new GroupMember(studyGroup, userId, role);
    }

    public void changeRole(GroupRole role) {
        if (role == null) {
            throw new IllegalArgumentException("그룹 역할은 필수입니다.");
        }
        this.role = role;
    }

    public void withdraw() {
        status = GroupMemberStatus.WITHDRAWN;
    }

    @PrePersist
    private void initializeJoinedAt() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }

    private static void validateJoinArguments(StudyGroup studyGroup, Long userId, GroupRole role) {
        if (studyGroup == null) {
            throw new IllegalArgumentException("그룹은 필수입니다.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자 식별자는 필수입니다.");
        }
        if (role == null) {
            throw new IllegalArgumentException("그룹 역할은 필수입니다.");
        }
    }

    public Long getId() {
        return id;
    }

    public StudyGroup getStudyGroup() {
        return studyGroup;
    }

    public Long getUserId() {
        return userId;
    }

    public GroupRole getRole() {
        return role;
    }

    public GroupMemberStatus getStatus() {
        return status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
