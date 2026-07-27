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
    private Long scheduleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

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

    public void changeResponse(AttendanceResponse response) {
        this.response = response;
        this.respondedAt = LocalDateTime.now();
    }
}
