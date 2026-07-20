package com.mycom.myapp.schedule.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.study.entity.StudyGroup;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class SchedulePageResponseTest {

    @Test
    void convertsEntityPageToStableApiResponse() {
        StudyGroup group = StudyGroup.create(25L, "알고리즘 스터디");
        ReflectionTestUtils.setField(group, "id", 10L);
        StudySchedule schedule =
                StudySchedule.create(
                        group,
                        1L,
                        "3주차 스터디",
                        LocalDateTime.of(2026, 7, 25, 19, 0),
                        "강의실 A",
                        null,
                        "3장 문제 풀이",
                        "교재",
                        LocalDateTime.of(2026, 7, 24, 18, 0));
        ReflectionTestUtils.setField(schedule, "id", 100L);

        SchedulePageResponse response =
                SchedulePageResponse.from(
                        new PageImpl<>(List.of(schedule), PageRequest.of(1, 1), 3));

        assertThat(response.items())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.scheduleId()).isEqualTo(100L);
                            assertThat(item.creatorId()).isEqualTo(1L);
                            assertThat(item.title()).isEqualTo("3주차 스터디");
                            assertThat(item.location()).isEqualTo("강의실 A");
                            assertThat(item.responseDeadline())
                                    .isEqualTo(LocalDateTime.of(2026, 7, 24, 18, 0));
                        });
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
    }
}
