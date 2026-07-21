package com.mycom.myapp.activity.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.activity.entity.ActivityRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ActivityRecordRepositoryTest {

    @Autowired private ActivityRecordRepository repository;

    @Test
    void reportsWhetherScheduleHasActivityRecord() {
        repository.saveAndFlush(ActivityRecord.forSchedule(10L));

        assertThat(repository.existsByScheduleId(10L)).isTrue();
        assertThat(repository.existsByScheduleId(99L)).isFalse();
    }
}
