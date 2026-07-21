package com.mycom.myapp.activity.repository;

import com.mycom.myapp.activity.entity.ActivityRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, Long> {

    Optional<ActivityRecord> findByScheduleId(Long scheduleId);
}
