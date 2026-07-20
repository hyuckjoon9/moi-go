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
        name = "attendance_responses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"schedule_id", "user_id"}))
public class AttendanceAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId; // study_schedules.id (study_schedules 도메인 소유 FK)

    @Column(name = "user_id", nullable = false)
    private Long userId; // users.id (user 도메인 소유 FK)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceResponse response;

    @Column(name = "responded_at", nullable = false)
    private LocalDateTime respondedAt;

    @Builder
    public AttendanceAnswer(Long scheduleId, Long userId, AttendanceResponse response) {
        this.scheduleId = scheduleId;
        this.userId = userId;
        this.response = response;
        this.respondedAt = LocalDateTime.now();
    }

    /** 참석 여부 응답 수정 (예: UNDECIDED → ATTEND로 변경) */
    public void changeResponse(AttendanceResponse response) {
        this.response = response;
        this.respondedAt = LocalDateTime.now();
    }
}
