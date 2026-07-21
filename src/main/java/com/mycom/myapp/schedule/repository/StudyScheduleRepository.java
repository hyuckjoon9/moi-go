package com.mycom.myapp.schedule.repository;

import com.mycom.myapp.schedule.entity.StudySchedule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyScheduleRepository extends JpaRepository<StudySchedule, Long> {

    List<StudySchedule> findAllByStudyGroupIdOrderByScheduledAtAsc(Long groupId);

    Page<StudySchedule>
            findAllByStudyGroupIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(
                    Long groupId, LocalDateTime scheduledAt, Pageable pageable);

    Page<StudySchedule> findAllByStudyGroupIdAndScheduledAtLessThanOrderByScheduledAtDescIdDesc(
            Long groupId, LocalDateTime scheduledAt, Pageable pageable);

    Optional<StudySchedule> findByIdAndStudyGroupId(Long id, Long groupId);
}
