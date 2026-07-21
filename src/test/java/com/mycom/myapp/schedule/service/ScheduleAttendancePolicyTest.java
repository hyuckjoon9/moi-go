package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.study.entity.GroupStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScheduleAttendancePolicyTest {

    private static final LocalDateTime SCHEDULED_AT = LocalDateTime.of(2026, 7, 25, 19, 0);

    @Test
    void usesExplicitResponseDeadlineWhenPresent() {
        LocalDateTime deadline = SCHEDULED_AT.minusHours(1);
        ScheduleAttendancePolicy policy =
                new ScheduleAttendancePolicy(
                        100L, 10L, GroupStatus.ACTIVE, true, SCHEDULED_AT, deadline);

        assertThat(policy.effectiveDeadline()).isEqualTo(deadline);
    }

    @Test
    void usesScheduledAtWhenResponseDeadlineIsNull() {
        ScheduleAttendancePolicy policy =
                new ScheduleAttendancePolicy(
                        100L, 10L, GroupStatus.ACTIVE, true, SCHEDULED_AT, null);

        assertThat(policy.effectiveDeadline()).isEqualTo(SCHEDULED_AT);
    }
}
