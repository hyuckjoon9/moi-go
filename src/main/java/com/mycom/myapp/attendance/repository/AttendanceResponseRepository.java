package com.mycom.myapp.attendance.repository;

import com.mycom.myapp.attendance.entity.AttendanceAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceResponseRepository extends JpaRepository<AttendanceAnswer, Long> {

    List<AttendanceAnswer> findByScheduleId(Long scheduleId);

    Optional<AttendanceAnswer> findByScheduleIdAndUserId(Long scheduleId, Long userId);
}
