package com.mycom.myapp.attendance.entity;

import jakarta.persistence.*;
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
        name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"schedule_id", "user_id"}))
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId; // study_schedules.id (study_schedules 도메인 소유 FK)

    @Column(name = "user_id", nullable = false)
    private Long userId; // 출석 대상자 users.id (user 도메인 소유 FK)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    @Column(name = "checked_by")
    private Long checkedBy; // 체크한 모집장의 userId. 자동 결석 처리 시에는 null

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Builder
    public AttendanceRecord(Long scheduleId, Long userId, AttendanceStatus status, Long checkedBy) {
        this.scheduleId = scheduleId;
        this.userId = userId;
        this.status = status;
        this.checkedBy = checkedBy;
        this.checkedAt = LocalDateTime.now();
    }

    /** 모집장이 출석 상태를 수정할 때 */
    public void updateStatus(AttendanceStatus status, Long checkedBy) {
        this.status = status;
        this.checkedBy = checkedBy;
        this.checkedAt = LocalDateTime.now();
    }
}
