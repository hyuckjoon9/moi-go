package com.mycom.myapp.activity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycom.myapp.activity.entity.ActivityRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ActivityRecordRepositoryTest {

    @Autowired private ActivityRecordRepository activityRecordRepository;

    @Test
    void findsRecordByScheduleId() {
        ActivityRecord record = save(10L, 1L);

        assertThat(activityRecordRepository.findByScheduleId(10L)).contains(record);
        assertThat(activityRecordRepository.findByScheduleId(99L)).isEmpty();
    }

    @Test
    void rejectsDuplicateScheduleId() {
        save(10L, 1L);

        assertThatThrownBy(
                        () ->
                                activityRecordRepository.saveAndFlush(
                                        ActivityRecord.builder()
                                                .scheduleId(10L)
                                                .authorId(2L)
                                                .topic("다른 토픽")
                                                .content("다른 내용")
                                                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ActivityRecord save(Long scheduleId, Long authorId) {
        return activityRecordRepository.saveAndFlush(
                ActivityRecord.builder()
                        .scheduleId(scheduleId)
                        .authorId(authorId)
                        .topic("토픽")
                        .content("내용")
                        .build());
    }
}
