package com.mycom.myapp.activity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "activity_records",
        uniqueConstraints = @UniqueConstraint(columnNames = "schedule_id"))
public class ActivityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    protected ActivityRecord() {}

    private ActivityRecord(Long scheduleId) {
        if (scheduleId == null) {
            throw new IllegalArgumentException("일정 식별자는 필수입니다.");
        }
        this.scheduleId = scheduleId;
    }

    public static ActivityRecord forSchedule(Long scheduleId) {
        return new ActivityRecord(scheduleId);
    }

    public Long getId() {
        return id;
    }

    public Long getScheduleId() {
        return scheduleId;
    }
}
