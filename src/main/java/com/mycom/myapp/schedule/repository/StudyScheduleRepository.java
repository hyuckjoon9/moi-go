package com.mycom.myapp.schedule.repository;

import com.mycom.myapp.schedule.entity.StudySchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyScheduleRepository extends JpaRepository<StudySchedule, Long> {

    List<StudySchedule> findAllByStudyGroupIdOrderByScheduledAtAsc(Long groupId);
}
