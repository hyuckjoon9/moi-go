package com.mycom.myapp.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AdminAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private AdminTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_label", nullable = false, length = 255)
    private String targetLabel;

    @Column(name = "before_snapshot", nullable = false, columnDefinition = "text")
    private String beforeSnapshot;

    @Column(name = "after_snapshot", nullable = false, columnDefinition = "text")
    private String afterSnapshot;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AdminAuditLog() {}

    private AdminAuditLog(
            Long adminId,
            AdminAction action,
            AdminTargetType targetType,
            Long targetId,
            String targetLabel,
            String beforeSnapshot,
            String afterSnapshot,
            String reason) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.reason = reason;
    }

    public static AdminAuditLog create(
            Long adminId,
            AdminAction action,
            AdminTargetType targetType,
            Long targetId,
            String targetLabel,
            String beforeSnapshot,
            String afterSnapshot,
            String reason) {
        if (adminId == null
                || action == null
                || targetType == null
                || targetId == null
                || targetLabel == null
                || targetLabel.isBlank()) {
            throw new IllegalArgumentException("운영 이력 식별 정보는 필수입니다.");
        }
        if (beforeSnapshot == null || afterSnapshot == null) {
            throw new IllegalArgumentException("변경 전후 상태는 필수입니다.");
        }

        String normalizedReason = reason == null ? "" : reason.strip();
        if (normalizedReason.length() < 5 || normalizedReason.length() > 500) {
            throw new IllegalArgumentException("조치 사유는 5자 이상 500자 이하여야 합니다.");
        }

        return new AdminAuditLog(
                adminId,
                action,
                targetType,
                targetId,
                targetLabel.strip(),
                beforeSnapshot,
                afterSnapshot,
                normalizedReason);
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

    public Long getAdminId() {
        return adminId;
    }

    public AdminAction getAction() {
        return action;
    }

    public AdminTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public String getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
