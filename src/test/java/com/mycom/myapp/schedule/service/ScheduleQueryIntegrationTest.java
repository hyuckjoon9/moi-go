package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mycom.myapp.schedule.dto.request.ScheduleScope;
import com.mycom.myapp.schedule.dto.response.SchedulePageResponse;
import com.mycom.myapp.schedule.dto.response.ScheduleResponse;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:schedule_query;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@Transactional
class ScheduleQueryIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 20, 12, 0);

    @Autowired private ScheduleService service;
    @Autowired private StudyGroupRepository groupRepository;
    @Autowired private GroupMemberRepository memberRepository;
    @Autowired private StudyScheduleRepository scheduleRepository;

    @MockitoBean(name = "scheduleClock")
    private Clock clock;

    @BeforeEach
    void fixClock() {
        when(clock.instant()).thenReturn(NOW.atZone(ZONE).toInstant());
        when(clock.getZone()).thenReturn(ZONE);
    }

    @Test
    void activeMemberCanReachEveryGroupScheduleAcrossScopesAndDetail() {
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(25L, "알고리즘 스터디"));
        memberRepository.saveAndFlush(GroupMember.join(group, 1L, GroupRole.MEMBER));
        StudySchedule oldPast = save(group, "오래된 일정", NOW.minusDays(2));
        StudySchedule recentPast = save(group, "최근 지난 일정", NOW.minusHours(1));
        StudySchedule boundary = save(group, "경계 일정", NOW);
        StudySchedule future = save(group, "예정 일정", NOW.plusDays(1));

        SchedulePageResponse upcoming =
                service.getSchedules(group.getId(), 1L, ScheduleScope.UPCOMING, 0, 20);
        SchedulePageResponse past =
                service.getSchedules(group.getId(), 1L, ScheduleScope.PAST, 0, 20);
        ScheduleResponse detail = service.getSchedule(group.getId(), 1L, recentPast.getId());

        assertThat(upcoming.items())
                .extracting(item -> item.scheduleId())
                .containsExactly(boundary.getId(), future.getId());
        assertThat(past.items())
                .extracting(item -> item.scheduleId())
                .containsExactly(recentPast.getId(), oldPast.getId());
        assertThat(upcoming.totalElements() + past.totalElements()).isEqualTo(4);
        assertThat(detail.scheduleId()).isEqualTo(recentPast.getId());
        assertThat(detail.title()).isEqualTo("최근 지난 일정");
    }

    private StudySchedule save(StudyGroup group, String title, LocalDateTime scheduledAt) {
        return scheduleRepository.saveAndFlush(
                StudySchedule.create(group, 1L, title, scheduledAt, null, null, null, null, null));
    }
}
