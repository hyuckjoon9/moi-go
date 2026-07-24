package com.mycom.myapp.attendance.repository;

import com.mycom.myapp.attendance.entity.AttendanceRecord;
import com.mycom.myapp.attendance.entity.AttendanceStatus;
import com.mycom.myapp.schedule.service.port.AttendanceRecordLookup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository
        extends JpaRepository<AttendanceRecord, Long>, AttendanceRecordLookup {

    List<AttendanceRecord> findByScheduleId(Long scheduleId);

    List<AttendanceRecord> findByScheduleIdIn(List<Long> scheduleIds);

    Optional<AttendanceRecord> findByScheduleIdAndUserId(Long scheduleId, Long userId);

    long countByUserIdAndStatus(Long userId, AttendanceStatus status);

    @Override
    boolean existsByScheduleId(Long scheduleId);
}
