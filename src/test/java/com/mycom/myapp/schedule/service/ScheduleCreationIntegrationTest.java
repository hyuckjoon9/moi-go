package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.dto.request.ScheduleCreateRequest;
import com.mycom.myapp.schedule.dto.response.ScheduleResponse;
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
            "spring.datasource.url=jdbc:h2:mem:schedule_creation;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@Transactional
class ScheduleCreationIntegrationTest {

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
    void createsAndPersistsScheduleInOneTransaction() {
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(25L, "알고리즘 스터디"));
        memberRepository.saveAndFlush(GroupMember.join(group, 1L, GroupRole.LEADER));
        ScheduleCreateRequest request =
                new ScheduleCreateRequest(
                        "3주차 스터디",
                        NOW.plusDays(3),
                        null,
                        null,
                        "3장 문제 풀이",
                        "교재와 노트북",
                        NOW.plusDays(2));

        ScheduleResponse response = service.create(group.getId(), 1L, request);

        assertThat(response.scheduleId()).isNotNull();
        assertThat(response.groupId()).isEqualTo(group.getId());
        assertThat(response.creatorId()).isEqualTo(1L);
        assertThat(scheduleRepository.findAll()).hasSize(1);
    }

    @Test
    void doesNotPersistInvalidScheduleTime() {
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(25L, "알고리즘 스터디"));
        memberRepository.saveAndFlush(GroupMember.join(group, 1L, GroupRole.LEADER));
        ScheduleCreateRequest request =
                new ScheduleCreateRequest("잘못된 일정", NOW, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(group.getId(), 1L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_SCHEDULE_TIME));
        assertThat(scheduleRepository.findAll()).isEmpty();
    }
}
