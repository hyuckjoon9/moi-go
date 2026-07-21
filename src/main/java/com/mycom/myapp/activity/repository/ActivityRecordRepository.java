package com.mycom.myapp.activity.repository;

import com.mycom.myapp.activity.entity.ActivityRecord;
import com.mycom.myapp.schedule.service.port.ActivityRecordLookup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRecordRepository
        extends JpaRepository<ActivityRecord, Long>, ActivityRecordLookup {

    Optional<ActivityRecord> findByScheduleId(Long scheduleId);

    @Override
    boolean existsByScheduleId(Long scheduleId);
}