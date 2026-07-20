package com.mycom.myapp.attendance.repository;

import com.mycom.myapp.attendance.entity.AttendanceRecord;
import com.mycom.myapp.attendance.entity.AttendanceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByScheduleId(Long scheduleId);

    Optional<AttendanceRecord> findByScheduleIdAndUserId(Long scheduleId, Long userId);

    long countByUserIdAndStatus(Long userId, AttendanceStatus status);
}
