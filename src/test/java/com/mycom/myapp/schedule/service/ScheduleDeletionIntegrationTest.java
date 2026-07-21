package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mycom.myapp.activity.entity.ActivityRecord;
import com.mycom.myapp.activity.repository.ActivityRecordRepository;
import com.mycom.myapp.attendance.entity.AttendanceAnswer;
import com.mycom.myapp.attendance.entity.AttendanceRecord;
import com.mycom.myapp.attendance.entity.AttendanceResponse;
import com.mycom.myapp.attendance.entity.AttendanceStatus;
import com.mycom.myapp.attendance.repository.AttendanceRecordRepository;
import com.mycom.myapp.attendance.repository.AttendanceResponseRepository;
import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:schedule_deletion;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@Sql(
        scripts = "/sql/schedule-deletion-fk.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Transactional
class ScheduleDeletionIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 20, 12, 0);

    @Autowired private ScheduleService service;
    @Autowired private StudyGroupRepository groupRepository;
    @Autowired private GroupMemberRepository memberRepository;
    @Autowired private StudyScheduleRepository scheduleRepository;
    @Autowired private AttendanceResponseRepository attendanceResponseRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private ActivityRecordRepository activityRecordRepository;

    @MockitoBean(name = "scheduleClock")
    private Clock clock;

    private long nextPostId = 100L;

    @BeforeEach
    void fixClock() {
        when(clock.instant()).thenReturn(NOW.atZone(ZONE).toInstant());
        when(clock.getZone()).thenReturn(ZONE);
    }

    @Test
    void deletingFutureScheduleCascadesAttendanceAnswers() {
        Fixture fixture = saveFutureScheduleWithLeader();
        attendanceResponseRepository.saveAndFlush(
                AttendanceAnswer.builder()
                        .scheduleId(fixture.schedule().getId())
                        .userId(2L)
                        .response(AttendanceResponse.ATTEND)
                        .build());

        service.delete(fixture.group().getId(), 1L, fixture.schedule().getId());

        assertThat(scheduleRepository.findById(fixture.schedule().getId())).isEmpty();
        assertThat(attendanceResponseRepository.findByScheduleId(fixture.schedule().getId()))
                .isEmpty();
    }

    @Test
    void attendanceRecordPreventsScheduleDeletion() {
        Fixture fixture = saveFutureScheduleWithLeader();
        attendanceRecordRepository.saveAndFlush(
                AttendanceRecord.builder()
                        .scheduleId(fixture.schedule().getId())
                        .userId(2L)
                        .status(AttendanceStatus.PRESENT)
                        .checkedBy(1L)
                        .build());

        assertDeleteNotAllowed(fixture);
        assertThat(scheduleRepository.findById(fixture.schedule().getId())).isPresent();
    }

    @Test
    void activityRecordPreventsScheduleDeletion() {
        Fixture fixture = saveFutureScheduleWithLeader();
        activityRecordRepository.saveAndFlush(
                ActivityRecord.forSchedule(fixture.schedule().getId()));

        assertDeleteNotAllowed(fixture);
    }

    private void assertDeleteNotAllowed(Fixture fixture) {
        assertThatThrownBy(
                        () ->
                                service.delete(
                                        fixture.group().getId(), 1L, fixture.schedule().getId()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SCHEDULE_DELETE_NOT_ALLOWED));
    }

    private Fixture saveFutureScheduleWithLeader() {
        StudyGroup group =
                groupRepository.saveAndFlush(StudyGroup.create(nextPostId++, "삭제 통합 테스트 그룹"));
        memberRepository.saveAndFlush(GroupMember.join(group, 1L, GroupRole.LEADER));
        StudySchedule schedule =
                scheduleRepository.saveAndFlush(
                        StudySchedule.create(
                                group,
                                1L,
                                "삭제 대상 일정",
                                NOW.plusDays(1),
                                null,
                                null,
                                null,
                                null,
                                null));
        return new Fixture(group, schedule);
    }

    private record Fixture(StudyGroup group, StudySchedule schedule) {}
}
