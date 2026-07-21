# Schedule Deletion and Response Deadline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 활성 관리자가 종속 이력을 보존하면서 미래 일정을 삭제하고 응답 마감을 별도 `PATCH`로 변경하며, Part4가 동일한 유효 마감 정책으로 참석 응답 등록·변경·삭제를 차단하게 한다.

**Architecture:** Part3가 작은 공개 포트와 `ScheduleAttendancePolicyService`를 소유하고 Part4·활동 Repository가 자기 데이터의 존재 여부 포트를 구현한다. Part4는 Part3 Repository가 아니라 읽기 전용 정책 포트를 사용한다. 서비스 사전 검사는 명확한 오류를 제공하고, DB의 `CASCADE`·`RESTRICT` FK는 동시 요청까지 포함한 최종 무결성을 보장한다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Web, Spring Data JPA, Bean Validation, JUnit 5, AssertJ, Mockito, H2 MySQL mode, MySQL 8.0+

## Global Constraints

- Part3 작업 전 `docs/part3-group/development-guide.md`, `context.md`, `erd.md`, `api.md`, `schedule-deletion-deadline-design.md`를 읽는다.
- Part3는 Part4·활동 Repository를 직접 import하거나 호출하지 않는다.
- 일정 삭제는 `scheduledAt > now`인 경우에만 허용한다.
- 참석 응답 FK는 `ON DELETE CASCADE`, 출석 기록과 활동 기록 FK는 `ON DELETE RESTRICT`다.
- `responseDeadline == null`이면 `scheduledAt`을 유효 마감으로 사용한다.
- 참석 응답 등록·변경·삭제는 모두 `now < effectiveDeadline`일 때만 허용한다.
- 기존 유효 마감 이후 재개방하지 않고 기존 참석 응답은 소급 삭제하거나 무효화하지 않는다.
- 시간 경계 테스트는 `Asia/Seoul` 고정 `Clock`을 사용한다.
- 다른 파트와 공통 영역 변경은 각각 독립 커밋으로 유지한다.
- 각 테스트는 실패를 확인한 뒤 최소 구현으로 통과시킨다.

## Workstream and File Map

### Shared contract workstream

- Create `src/main/java/com/mycom/myapp/schedule/service/port/AttendanceRecordLookup.java`: Part4 출석 기록 존재 조회 포트
- Create `src/main/java/com/mycom/myapp/schedule/service/port/ActivityRecordLookup.java`: 활동 기록 존재 조회 포트
- Create `src/main/java/com/mycom/myapp/schedule/service/port/ScheduleAttendancePolicyReader.java`: Part4가 사용하는 일정 정책 조회 포트
- Create `src/main/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicy.java`: 파트 간 읽기 전용 정책 값
- Create `src/main/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicyService.java`: 일정·그룹원 조회와 정책 조립
- Modify `src/main/java/com/mycom/myapp/global/exception/ErrorCode.java`: 합의된 오류 세 개 추가

### Part4 and activity adapter workstream

- Modify `src/main/java/com/mycom/myapp/attendance/repository/AttendanceRecordRepository.java`: `AttendanceRecordLookup` 구현
- Modify `src/main/java/com/mycom/myapp/activity/entity/ActivityRecord.java`: `schedule_id` 최소 영속성 매핑
- Modify `src/main/java/com/mycom/myapp/activity/repository/ActivityRecordRepository.java`: `ActivityRecordLookup` 구현
- Modify `src/main/java/com/mycom/myapp/attendance/service/AttendanceService.java`: 응답 마감 정책 적용

### Part3 use-case workstream

- Create `src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleDeadlineUpdateRequest.java`: 누락과 명시적 `null`을 구분하는 PATCH 요청
- Modify `src/main/java/com/mycom/myapp/schedule/entity/StudySchedule.java`: 응답 마감 변경 도메인 메서드
- Modify `src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java`: 삭제와 마감 변경 유스케이스
- Modify `src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java`: `DELETE`와 `PATCH` 매핑
- Modify `src/main/java/com/mycom/myapp/schedule/repository/StudyScheduleRepository.java`: 기존 `delete`·`flush` 사용, 새 쿼리 불필요

### Verification and documentation workstream

- Create `src/test/resources/sql/schedule-deletion-fk.sql`: H2 통합 테스트용 FK 계약
- Create `src/test/java/com/mycom/myapp/schedule/service/ScheduleDeletionIntegrationTest.java`: CASCADE·RESTRICT 통합 검증
- Modify `docs/part3-group/api.md`: 구현된 API 계약 반영
- Modify `docs/part4-attendance/api.md`: 유효 마감과 오류 계약 반영
- Modify `docs/part3-group/context.md`: 완료 상태와 검증 결과 반영

---

### Task 1: Shared policy types and error codes

**Files:**
- Create: `src/main/java/com/mycom/myapp/schedule/service/port/AttendanceRecordLookup.java`
- Create: `src/main/java/com/mycom/myapp/schedule/service/port/ActivityRecordLookup.java`
- Create: `src/main/java/com/mycom/myapp/schedule/service/port/ScheduleAttendancePolicyReader.java`
- Create: `src/main/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicy.java`
- Modify: `src/main/java/com/mycom/myapp/global/exception/ErrorCode.java`
- Test: `src/test/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicyTest.java`

**Interfaces:**
- Produces: `boolean AttendanceRecordLookup.existsByScheduleId(Long scheduleId)`
- Produces: `boolean ActivityRecordLookup.existsByScheduleId(Long scheduleId)`
- Produces: `ScheduleAttendancePolicy ScheduleAttendancePolicyReader.getAttendancePolicy(Long scheduleId, Long userId)`
- Produces: `LocalDateTime ScheduleAttendancePolicy.effectiveDeadline()`

- [ ] **Step 1: Write the failing policy value test**

```java
package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.study.entity.GroupStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScheduleAttendancePolicyTest {

    private static final LocalDateTime SCHEDULED_AT =
            LocalDateTime.of(2026, 7, 25, 19, 0);

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
```

- [ ] **Step 2: Run the test and confirm the type is missing**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleAttendancePolicyTest"
```

Expected: compilation fails because `ScheduleAttendancePolicy` does not exist.

- [ ] **Step 3: Add the exact shared types**

```java
package com.mycom.myapp.schedule.service;

import com.mycom.myapp.study.entity.GroupStatus;
import java.time.LocalDateTime;

public record ScheduleAttendancePolicy(
        Long scheduleId,
        Long groupId,
        GroupStatus groupStatus,
        boolean activeGroupMember,
        LocalDateTime scheduledAt,
        LocalDateTime responseDeadline) {

    public LocalDateTime effectiveDeadline() {
        return responseDeadline != null ? responseDeadline : scheduledAt;
    }
}
```

```java
package com.mycom.myapp.schedule.service.port;

public interface AttendanceRecordLookup {
    boolean existsByScheduleId(Long scheduleId);
}
```

```java
package com.mycom.myapp.schedule.service.port;

public interface ActivityRecordLookup {
    boolean existsByScheduleId(Long scheduleId);
}
```

```java
package com.mycom.myapp.schedule.service.port;

import com.mycom.myapp.schedule.service.ScheduleAttendancePolicy;

public interface ScheduleAttendancePolicyReader {
    ScheduleAttendancePolicy getAttendancePolicy(Long scheduleId, Long userId);
}
```

Add these enum constants immediately after `SCHEDULE_UPDATE_NOT_ALLOWED`:

```java
SCHEDULE_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "출석 또는 활동 이력이 있거나 이미 시작된 일정은 삭제할 수 없습니다."),
SCHEDULE_DEADLINE_UPDATE_NOT_ALLOWED(HttpStatus.CONFLICT, "마감되었거나 이미 시작된 일정의 응답 마감은 변경할 수 없습니다."),
ATTENDANCE_RESPONSE_CLOSED(HttpStatus.CONFLICT, "참석 응답 마감 시간이 지났습니다."),
```

- [ ] **Step 4: Run the focused test**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleAttendancePolicyTest"
```

Expected: `BUILD SUCCESSFUL` and 2 tests pass.

- [ ] **Step 5: Commit the shared contract**

```powershell
git add src/main/java/com/mycom/myapp/global/exception/ErrorCode.java src/main/java/com/mycom/myapp/schedule/service/port/AttendanceRecordLookup.java src/main/java/com/mycom/myapp/schedule/service/port/ActivityRecordLookup.java src/main/java/com/mycom/myapp/schedule/service/port/ScheduleAttendancePolicyReader.java src/main/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicy.java src/test/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicyTest.java
git commit -m "feat: 일정 삭제와 참석 응답 정책 계약 추가"
```

---

### Task 2: Attendance and activity dependency adapters

**Files:**
- Modify: `src/main/java/com/mycom/myapp/attendance/repository/AttendanceRecordRepository.java`
- Modify: `src/main/java/com/mycom/myapp/activity/entity/ActivityRecord.java`
- Modify: `src/main/java/com/mycom/myapp/activity/repository/ActivityRecordRepository.java`
- Modify: `src/test/java/com/mycom/myapp/attendance/repository/AttendanceRecordRepositoryTest.java`
- Create: `src/test/java/com/mycom/myapp/activity/repository/ActivityRecordRepositoryTest.java`

**Interfaces:**
- Consumes: `AttendanceRecordLookup.existsByScheduleId(Long)`
- Consumes: `ActivityRecordLookup.existsByScheduleId(Long)`
- Produces: Spring Data beans that satisfy both lookup ports without exposing Repository types to Part3

- [ ] **Step 1: Add failing existence-query tests**

Add to `AttendanceRecordRepositoryTest`:

```java
@Test
void reportsWhetherScheduleHasAttendanceRecords() {
    save(10L, 20L, AttendanceStatus.PRESENT, 1L);

    assertThat(attendanceRecordRepository.existsByScheduleId(10L)).isTrue();
    assertThat(attendanceRecordRepository.existsByScheduleId(99L)).isFalse();
}
```

Create `ActivityRecordRepositoryTest`:

```java
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
```

- [ ] **Step 2: Run both tests and confirm missing methods/mapping**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.attendance.repository.AttendanceRecordRepositoryTest" --tests "com.mycom.myapp.activity.repository.ActivityRecordRepositoryTest"
```

Expected: compilation fails for `existsByScheduleId` and `ActivityRecord.forSchedule`.

- [ ] **Step 3: Implement the repository adapters**

Replace `AttendanceRecordRepository` with the complete interface below:

```java
package com.mycom.myapp.attendance.repository;

import com.mycom.myapp.attendance.entity.AttendanceRecord;
import com.mycom.myapp.attendance.entity.AttendanceStatus;
import com.mycom.myapp.schedule.service.port.AttendanceRecordLookup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository
        extends JpaRepository<AttendanceRecord, Long>, AttendanceRecordLookup {

    List<AttendanceRecord> findByScheduleId(Long scheduleId);

    Optional<AttendanceRecord> findByScheduleIdAndUserId(Long scheduleId, Long userId);

    long countByUserIdAndStatus(Long userId, AttendanceStatus status);

    @Override
    boolean existsByScheduleId(Long scheduleId);
}
```

Replace the activity entity skeleton with:

```java
package com.mycom.myapp.activity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "activity_records",
        uniqueConstraints = @UniqueConstraint(columnNames = "schedule_id"))
public class ActivityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    protected ActivityRecord() {}

    private ActivityRecord(Long scheduleId) {
        if (scheduleId == null) {
            throw new IllegalArgumentException("일정 식별자는 필수입니다.");
        }
        this.scheduleId = scheduleId;
    }

    public static ActivityRecord forSchedule(Long scheduleId) {
        return new ActivityRecord(scheduleId);
    }

    public Long getId() {
        return id;
    }

    public Long getScheduleId() {
        return scheduleId;
    }
}
```

Replace the activity repository skeleton with:

```java
package com.mycom.myapp.activity.repository;

import com.mycom.myapp.activity.entity.ActivityRecord;
import com.mycom.myapp.schedule.service.port.ActivityRecordLookup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRecordRepository
        extends JpaRepository<ActivityRecord, Long>, ActivityRecordLookup {

    @Override
    boolean existsByScheduleId(Long scheduleId);
}
```

- [ ] **Step 4: Run both repository tests**

Run the same focused Gradle command from Step 2.

Expected: `BUILD SUCCESSFUL`; both existence queries return true and false as asserted.

- [ ] **Step 5: Commit external adapters separately**

```powershell
git add src/main/java/com/mycom/myapp/attendance/repository/AttendanceRecordRepository.java src/test/java/com/mycom/myapp/attendance/repository/AttendanceRecordRepositoryTest.java
git commit -m "feat: 일정별 출석 기록 존재 조회 추가"

git add src/main/java/com/mycom/myapp/activity/entity/ActivityRecord.java src/main/java/com/mycom/myapp/activity/repository/ActivityRecordRepository.java src/test/java/com/mycom/myapp/activity/repository/ActivityRecordRepositoryTest.java
git commit -m "feat: 일정별 활동 기록 존재 조회 기반 추가"
```

---

### Task 3: Part3 attendance policy reader

**Files:**
- Create: `src/main/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicyService.java`
- Create: `src/test/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicyServiceTest.java`

**Interfaces:**
- Consumes: `StudyScheduleRepository.findById(Long)`
- Consumes: `GroupMemberRepository.findByStudyGroupIdAndUserId(Long, Long)`
- Produces: `ScheduleAttendancePolicyReader.getAttendancePolicy(Long, Long)`

- [ ] **Step 1: Write failing policy-reader tests**

```java
package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScheduleAttendancePolicyServiceTest {

    private final StudyScheduleRepository scheduleRepository = mock(StudyScheduleRepository.class);
    private final GroupMemberRepository memberRepository = mock(GroupMemberRepository.class);
    private final ScheduleAttendancePolicyService service =
            new ScheduleAttendancePolicyService(scheduleRepository, memberRepository);

    @Test
    void returnsPolicyForActiveGroupMember() {
        StudyGroup group = StudyGroup.create(25L, "스터디");
        ReflectionTestUtils.setField(group, "id", 10L);
        StudySchedule schedule =
                StudySchedule.create(
                        group,
                        1L,
                        "일정",
                        LocalDateTime.of(2026, 7, 25, 19, 0),
                        null,
                        null,
                        null,
                        null,
                        null);
        ReflectionTestUtils.setField(schedule, "id", 100L);
        when(scheduleRepository.findById(100L)).thenReturn(Optional.of(schedule));
        when(memberRepository.findByStudyGroupIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(GroupMember.join(group, 2L, GroupRole.MEMBER)));

        ScheduleAttendancePolicy policy = service.getAttendancePolicy(100L, 2L);

        assertThat(policy.scheduleId()).isEqualTo(100L);
        assertThat(policy.groupId()).isEqualTo(10L);
        assertThat(policy.activeGroupMember()).isTrue();
        assertThat(policy.effectiveDeadline()).isEqualTo(policy.scheduledAt());
    }

    @Test
    void rejectsMissingSchedule() {
        when(scheduleRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAttendancePolicy(100L, 2L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND));
    }
}
```

Add the missing and withdrawn membership tests:

```java
@Test
void rejectsMissingMembership() {
    StudyGroup group = StudyGroup.create(25L, "스터디");
    ReflectionTestUtils.setField(group, "id", 10L);
    StudySchedule schedule =
            StudySchedule.create(
                    group,
                    1L,
                    "일정",
                    LocalDateTime.of(2026, 7, 25, 19, 0),
                    null,
                    null,
                    null,
                    null,
                    null);
    ReflectionTestUtils.setField(schedule, "id", 100L);
    when(scheduleRepository.findById(100L)).thenReturn(Optional.of(schedule));
    when(memberRepository.findByStudyGroupIdAndUserId(10L, 2L))
            .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getAttendancePolicy(100L, 2L))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.GROUP_ACCESS_DENIED));
}

@Test
void rejectsWithdrawnMembership() {
    StudyGroup group = StudyGroup.create(25L, "스터디");
    ReflectionTestUtils.setField(group, "id", 10L);
    StudySchedule schedule =
            StudySchedule.create(
                    group,
                    1L,
                    "일정",
                    LocalDateTime.of(2026, 7, 25, 19, 0),
                    null,
                    null,
                    null,
                    null,
                    null);
    ReflectionTestUtils.setField(schedule, "id", 100L);
    GroupMember withdrawn = GroupMember.join(group, 2L, GroupRole.MEMBER);
    withdrawn.withdraw();
    when(scheduleRepository.findById(100L)).thenReturn(Optional.of(schedule));
    when(memberRepository.findByStudyGroupIdAndUserId(10L, 2L))
            .thenReturn(Optional.of(withdrawn));

    assertThatThrownBy(() -> service.getAttendancePolicy(100L, 2L))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.WITHDRAWN_GROUP_MEMBER));
}
```

- [ ] **Step 2: Run the test and confirm the service is missing**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleAttendancePolicyServiceTest"
```

Expected: compilation fails because `ScheduleAttendancePolicyService` does not exist.

- [ ] **Step 3: Implement the policy reader**

```java
package com.mycom.myapp.schedule.service;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.schedule.service.port.ScheduleAttendancePolicyReader;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScheduleAttendancePolicyService implements ScheduleAttendancePolicyReader {

    private final StudyScheduleRepository scheduleRepository;
    private final GroupMemberRepository memberRepository;

    public ScheduleAttendancePolicyService(
            StudyScheduleRepository scheduleRepository, GroupMemberRepository memberRepository) {
        this.scheduleRepository = scheduleRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    public ScheduleAttendancePolicy getAttendancePolicy(Long scheduleId, Long userId) {
        StudySchedule schedule =
                scheduleRepository
                        .findById(scheduleId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        Long groupId = schedule.getStudyGroup().getId();
        GroupMember member =
                memberRepository
                        .findByStudyGroupIdAndUserId(groupId, userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));
        if (member.getStatus() == GroupMemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_GROUP_MEMBER);
        }
        return new ScheduleAttendancePolicy(
                schedule.getId(),
                groupId,
                schedule.getStudyGroup().getStatus(),
                true,
                schedule.getScheduledAt(),
                schedule.getResponseDeadline());
    }
}
```

- [ ] **Step 4: Run the focused test**

Run the command from Step 2.

Expected: `BUILD SUCCESSFUL`; active, missing-schedule, missing-member and withdrawn-member cases pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicyService.java src/test/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicyServiceTest.java
git commit -m "feat: 참석 응답용 일정 정책 조회 추가"
```

---

### Task 4: Part4 response-deadline enforcement

**Files:**
- Modify: `src/main/java/com/mycom/myapp/attendance/service/AttendanceService.java`
- Modify: `src/test/java/com/mycom/myapp/attendance/service/AttendanceServiceTest.java`
- Modify: `docs/part4-attendance/api.md`

**Interfaces:**
- Consumes: `ScheduleAttendancePolicyReader.getAttendancePolicy(Long scheduleId, Long userId)`
- Consumes: bean `@Qualifier("scheduleClock") Clock`
- Produces: identical cutoff behavior for answer registration, change and deletion

- [ ] **Step 1: Add failing deadline tests**

Replace `@InjectMocks` construction with explicit mocks and a fixed clock:

```java
private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 21, 12, 0);

@Mock private AttendanceRecordRepository attendanceRecordRepository;
@Mock private AttendanceResponseRepository attendanceResponseRepository;
@Mock private ScheduleAttendancePolicyReader policyReader;

private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
private AttendanceService attendanceService;

@BeforeEach
void setUp() {
    attendanceService =
            new AttendanceService(
                    attendanceRecordRepository, attendanceResponseRepository, policyReader, clock);
    lenient()
            .when(policyReader.getAttendancePolicy(anyLong(), anyLong()))
            .thenReturn(
                    new ScheduleAttendancePolicy(
                            10L,
                            1L,
                            GroupStatus.ACTIVE,
                            true,
                            NOW.plusHours(2),
                            NOW.plusHours(1)));
}
```

Add these tests:

```java
@ParameterizedTest
@MethodSource("closedPolicies")
void rejectsAnswerSubmissionAtOrAfterEffectiveDeadline(ScheduleAttendancePolicy policy) {
    given(policyReader.getAttendancePolicy(10L, 20L)).willReturn(policy);

    assertThatThrownBy(
                    () ->
                            attendanceService.submitAnswer(
                                    10L,
                                    20L,
                                    new AttendanceAnswerRequest(AttendanceResponse.ATTEND)))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.ATTENDANCE_RESPONSE_CLOSED));
    verifyNoInteractions(attendanceResponseRepository);
}

@Test
void rejectsAnswerChangeAfterEffectiveDeadline() {
    given(policyReader.getAttendancePolicy(10L, 20L))
            .willReturn(
                    new ScheduleAttendancePolicy(
                            10L,
                            1L,
                            GroupStatus.ACTIVE,
                            true,
                            NOW.plusHours(2),
                            NOW.minusSeconds(1)));

    assertThatThrownBy(
                    () ->
                            attendanceService.changeAnswer(
                                    10L,
                                    20L,
                                    new AttendanceAnswerRequest(AttendanceResponse.ATTEND)))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.ATTENDANCE_RESPONSE_CLOSED));
    verifyNoInteractions(attendanceResponseRepository);
}

@Test
void usesScheduledAtAsDeadlineWhenExplicitDeadlineIsNull() {
    given(policyReader.getAttendancePolicy(10L, 20L))
            .willReturn(
                    new ScheduleAttendancePolicy(
                            10L, 1L, GroupStatus.ACTIVE, true, NOW, null));

    assertThatThrownBy(
                    () ->
                            attendanceService.deleteAnswer(10L, 20L))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.ATTENDANCE_RESPONSE_CLOSED));
}

@Test
void rejectsAnswerChangeForEndedGroup() {
    given(policyReader.getAttendancePolicy(10L, 20L))
            .willReturn(
                    new ScheduleAttendancePolicy(
                            10L,
                            1L,
                            GroupStatus.ENDED,
                            true,
                            NOW.plusHours(2),
                            NOW.plusHours(1)));

    assertThatThrownBy(
                    () ->
                            attendanceService.changeAnswer(
                                    10L,
                                    20L,
                                    new AttendanceAnswerRequest(AttendanceResponse.ATTEND)))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GROUP_ENDED));
}

private static Stream<ScheduleAttendancePolicy> closedPolicies() {
    return Stream.of(
            new ScheduleAttendancePolicy(10L, 1L, GroupStatus.ACTIVE, true, NOW.plusHours(1), NOW),
            new ScheduleAttendancePolicy(
                    10L, 1L, GroupStatus.ACTIVE, true, NOW.minusSeconds(1), null));
}
```

- [ ] **Step 2: Run the Part4 service test and confirm it fails**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.attendance.service.AttendanceServiceTest"
```

Expected: compilation fails because the four-argument constructor and deadline validation do not exist.

- [ ] **Step 3: Add policy and clock dependencies**

Remove `@RequiredArgsConstructor` from `AttendanceService` and add:

```java
private final ScheduleAttendancePolicyReader policyReader;
private final Clock clock;

public AttendanceService(
        AttendanceRecordRepository attendanceRecordRepository,
        AttendanceResponseRepository attendanceResponseRepository,
        ScheduleAttendancePolicyReader policyReader,
        @Qualifier("scheduleClock") Clock clock) {
    this.attendanceRecordRepository = attendanceRecordRepository;
    this.attendanceResponseRepository = attendanceResponseRepository;
    this.policyReader = policyReader;
    this.clock = clock;
}
```

Call this method as the first statement of `submitAnswer`, `changeAnswer` and `deleteAnswer`:

```java
validateAnswerOpen(scheduleId, userId);
```

Add:

```java
private void validateAnswerOpen(Long scheduleId, Long userId) {
    ScheduleAttendancePolicy policy = policyReader.getAttendancePolicy(scheduleId, userId);
    if (policy.groupStatus() == GroupStatus.ENDED) {
        throw new BusinessException(ErrorCode.GROUP_ENDED);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    if (!now.isBefore(policy.effectiveDeadline())) {
        throw new BusinessException(ErrorCode.ATTENDANCE_RESPONSE_CLOSED);
    }
}
```

- [ ] **Step 4: Run Part4 tests**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.attendance.*"
```

Expected: `BUILD SUCCESSFUL`; existing answer behavior and new deadline boundaries pass.

- [ ] **Step 5: Update Part4 API documentation and commit**

Document the effective deadline formula, active-group/member requirement, all three blocked mutations and
`ATTENDANCE_RESPONSE_CLOSED` in `docs/part4-attendance/api.md`.

```powershell
git add src/main/java/com/mycom/myapp/attendance/service/AttendanceService.java src/test/java/com/mycom/myapp/attendance/service/AttendanceServiceTest.java docs/part4-attendance/api.md
git commit -m "feat: 참석 응답 마감 검증 추가"
```

---

### Task 5: Deadline PATCH request and entity behavior

**Files:**
- Create: `src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleDeadlineUpdateRequest.java`
- Create: `src/test/java/com/mycom/myapp/schedule/dto/request/ScheduleDeadlineUpdateRequestTest.java`
- Modify: `src/main/java/com/mycom/myapp/schedule/entity/StudySchedule.java`
- Modify: `src/test/java/com/mycom/myapp/schedule/entity/StudyScheduleTest.java`

**Interfaces:**
- Produces: `ScheduleDeadlineUpdateRequest.responseDeadline()`
- Produces: validation property `responseDeadlineProvided == true`
- Produces: `StudySchedule.updateResponseDeadline(LocalDateTime, LocalDateTime)`

- [ ] **Step 1: Write failing DTO and entity tests**

DTO test core:

```java
@Test
void explicitNullCountsAsProvidedDeadline() {
    ScheduleDeadlineUpdateRequest request = new ScheduleDeadlineUpdateRequest();
    request.setResponseDeadline(null);

    assertThat(validator.validate(request)).isEmpty();
    assertThat(request.responseDeadline()).isNull();
}

@Test
void missingPropertyFailsValidation() {
    ScheduleDeadlineUpdateRequest request = new ScheduleDeadlineUpdateRequest();

    assertThat(validator.validate(request))
            .extracting(ConstraintViolation::getMessage)
            .containsExactly("responseDeadline 필드는 필수입니다.");
}
```

Entity test core:

```java
@Test
void updatesAndRemovesResponseDeadline() {
    StudySchedule schedule = futureSchedule(NOW.plusHours(3), NOW.plusHours(1));

    schedule.updateResponseDeadline(NOW.plusHours(2), NOW);
    assertThat(schedule.getResponseDeadline()).isEqualTo(NOW.plusHours(2));
    assertThat(schedule.getUpdatedAt()).isEqualTo(NOW);

    schedule.updateResponseDeadline(null, NOW.plusMinutes(1));
    assertThat(schedule.getResponseDeadline()).isNull();
}
```

- [ ] **Step 2: Run the focused tests and confirm missing types/methods**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.dto.request.ScheduleDeadlineUpdateRequestTest" --tests "com.mycom.myapp.schedule.entity.StudyScheduleTest"
```

Expected: compilation fails for the missing request and entity method.

- [ ] **Step 3: Implement the request and entity method**

```java
package com.mycom.myapp.schedule.dto.request;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDateTime;

public class ScheduleDeadlineUpdateRequest {

    private LocalDateTime responseDeadline;
    private boolean responseDeadlineProvided;

    public ScheduleDeadlineUpdateRequest() {}

    public void setResponseDeadline(LocalDateTime responseDeadline) {
        this.responseDeadline = responseDeadline;
        this.responseDeadlineProvided = true;
    }

    public LocalDateTime responseDeadline() {
        return responseDeadline;
    }

    @AssertTrue(message = "responseDeadline 필드는 필수입니다.")
    public boolean isResponseDeadlineProvided() {
        return responseDeadlineProvided;
    }
}
```

Add to `StudySchedule`:

```java
public void updateResponseDeadline(
        LocalDateTime responseDeadline, LocalDateTime modifiedAt) {
    if (modifiedAt == null) {
        throw new IllegalArgumentException("일정 수정 시각은 필수입니다.");
    }
    if (responseDeadline != null && responseDeadline.isAfter(scheduledAt)) {
        throw new IllegalArgumentException("응답 마감 시간은 일정 시간보다 늦을 수 없습니다.");
    }
    this.responseDeadline = responseDeadline;
    this.updatedAt = modifiedAt;
}
```

- [ ] **Step 4: Run the focused tests**

Run the command from Step 2.

Expected: `BUILD SUCCESSFUL`; explicit null, missing property and entity mutation pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleDeadlineUpdateRequest.java src/test/java/com/mycom/myapp/schedule/dto/request/ScheduleDeadlineUpdateRequestTest.java src/main/java/com/mycom/myapp/schedule/entity/StudySchedule.java src/test/java/com/mycom/myapp/schedule/entity/StudyScheduleTest.java
git commit -m "feat: 일정 응답 마감 변경 모델 추가"
```

---

### Task 6: Part3 response-deadline PATCH use case

**Files:**
- Modify: `src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java`
- Modify: `src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java`
- Modify: `src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java`
- Modify: `src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java`
- Modify: `src/test/java/com/mycom/myapp/schedule/service/ScheduleUpdateIntegrationTest.java`

**Interfaces:**
- Consumes: `ScheduleDeadlineUpdateRequest.responseDeadline()`
- Produces: `ScheduleService.updateResponseDeadline(Long, Long, Long, ScheduleDeadlineUpdateRequest)`
- Produces: `PATCH /api/groups/{groupId}/schedules/{scheduleId}/response-deadline`

- [ ] **Step 1: Add failing service boundary tests**

Add these service tests:

```java
@ParameterizedTest
@EnumSource(value = GroupRole.class, names = {"LEADER", "MANAGER"})
void changesDeadlineBeforeCurrentEffectiveDeadline(GroupRole role) {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, role));
    StudySchedule schedule = schedule(group, 100L, NOW.plusHours(3), NOW.plusHours(1));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule));
    ScheduleDeadlineUpdateRequest request = deadlineRequest(NOW.plusHours(2));

    ScheduleResponse response =
            service.updateResponseDeadline(10L, 1L, 100L, request);

    assertThat(response.responseDeadline()).isEqualTo(NOW.plusHours(2));
    assertThat(response.updatedAt()).isEqualTo(NOW);
}

@Test
void rejectsDeadlineReopeningAfterCurrentDeadline() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule(group, 100L, NOW.plusHours(3), NOW)));

    assertDeadlineError(
            ErrorCode.SCHEDULE_DEADLINE_UPDATE_NOT_ALLOWED,
            deadlineRequest(NOW.plusHours(2)));
}

@Test
void rejectsNewDeadlineAtNowOrAfterSchedule() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule(group, 100L, NOW.plusHours(3), null)));

    assertDeadlineError(ErrorCode.INVALID_SCHEDULE_TIME, deadlineRequest(NOW));
    assertDeadlineError(ErrorCode.INVALID_SCHEDULE_TIME, deadlineRequest(NOW.plusHours(4)));
}
```

Add the remaining validation-order tests and helpers:

```java
@Test
void removesDeadlineBeforeCurrentEffectiveDeadline() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    StudySchedule schedule = schedule(group, 100L, NOW.plusHours(3), NOW.plusHours(1));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule));

    ScheduleResponse response =
            service.updateResponseDeadline(10L, 1L, 100L, deadlineRequest(null));

    assertThat(response.responseDeadline()).isNull();
}

@Test
void rejectsDeadlineChangeForMissingGroup() {
    when(groupRepository.findById(10L)).thenReturn(Optional.empty());

    assertDeadlineError(ErrorCode.GROUP_NOT_FOUND, deadlineRequest(NOW.plusHours(1)));

    verifyNoInteractions(memberRepository, scheduleRepository);
}

@Test
void rejectsDeadlineChangeForMissingMembership() {
    StudyGroup group = activeGroup();
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(memberRepository.findByStudyGroupIdAndUserId(10L, 1L))
            .thenReturn(Optional.empty());

    assertDeadlineError(ErrorCode.GROUP_ACCESS_DENIED, deadlineRequest(NOW.plusHours(1)));

    verifyNoInteractions(scheduleRepository);
}

@Test
void rejectsDeadlineChangeForWithdrawnMember() {
    StudyGroup group = activeGroup();
    GroupMember member = GroupMember.join(group, 1L, GroupRole.LEADER);
    member.withdraw();
    allow(group, member);

    assertDeadlineError(
            ErrorCode.WITHDRAWN_GROUP_MEMBER, deadlineRequest(NOW.plusHours(1)));

    verifyNoInteractions(scheduleRepository);
}

@Test
void rejectsDeadlineChangeForEndedGroup() {
    StudyGroup group = activeGroup();
    group.end();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));

    assertDeadlineError(ErrorCode.GROUP_ENDED, deadlineRequest(NOW.plusHours(1)));

    verifyNoInteractions(scheduleRepository);
}

@Test
void rejectsDeadlineChangeForRegularMember() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));

    assertDeadlineError(
            ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN,
            deadlineRequest(NOW.plusHours(1)));

    verifyNoInteractions(scheduleRepository);
}

@Test
void reportsMissingScheduleBeforeDeadlineValidation() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.empty());

    assertDeadlineError(
            ErrorCode.SCHEDULE_NOT_FOUND, deadlineRequest(NOW.minusHours(1)));
}

@Test
void allowsNewDeadlineEqualToScheduledAt() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    LocalDateTime scheduledAt = NOW.plusHours(3);
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule(group, 100L, scheduledAt, null)));

    ScheduleResponse response =
            service.updateResponseDeadline(
                    10L, 1L, 100L, deadlineRequest(scheduledAt));

    assertThat(response.responseDeadline()).isEqualTo(scheduledAt);
}

private ScheduleDeadlineUpdateRequest deadlineRequest(LocalDateTime value) {
    ScheduleDeadlineUpdateRequest request = new ScheduleDeadlineUpdateRequest();
    request.setResponseDeadline(value);
    return request;
}

private void assertDeadlineError(
        ErrorCode expected, ScheduleDeadlineUpdateRequest request) {
    assertThatThrownBy(() -> service.updateResponseDeadline(10L, 1L, 100L, request))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
}
```

- [ ] **Step 2: Run the service test and confirm the method is missing**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleServiceTest"
```

Expected: compilation fails because `updateResponseDeadline` does not exist.

- [ ] **Step 3: Implement the service method**

```java
@Transactional
public ScheduleResponse updateResponseDeadline(
        Long groupId,
        Long memberId,
        Long scheduleId,
        ScheduleDeadlineUpdateRequest request) {
    StudyGroup group = getGroup(groupId);
    GroupMember member = getActiveMember(groupId, memberId);
    validateScheduleManagement(group, member);
    StudySchedule schedule =
            scheduleRepository
                    .findByIdAndStudyGroupId(scheduleId, groupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime currentEffectiveDeadline =
            schedule.getResponseDeadline() != null
                    ? schedule.getResponseDeadline()
                    : schedule.getScheduledAt();
    if (!schedule.getScheduledAt().isAfter(now)
            || !currentEffectiveDeadline.isAfter(now)) {
        throw new BusinessException(ErrorCode.SCHEDULE_DEADLINE_UPDATE_NOT_ALLOWED);
    }
    LocalDateTime newDeadline = request.responseDeadline();
    if (newDeadline != null
            && (!newDeadline.isAfter(now)
                    || newDeadline.isAfter(schedule.getScheduledAt()))) {
        throw new BusinessException(ErrorCode.INVALID_SCHEDULE_TIME);
    }
    schedule.updateResponseDeadline(newDeadline, now);
    return ScheduleResponse.from(schedule);
}
```

- [ ] **Step 4: Add controller tests and mapping**

Controller test cases:

```java
mockMvc.perform(
                patch("/api/groups/10/schedules/100/response-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseDeadline\":null}"))
        .andExpect(status().isOk());

mockMvc.perform(
                patch("/api/groups/10/schedules/100/response-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
        .andExpect(status().isBadRequest());
```

Add the endpoint:

```java
@PatchMapping("/{scheduleId}/response-deadline")
public ApiResponse<ScheduleResponse> updateResponseDeadline(
        @PathVariable("groupId") Long groupId,
        @PathVariable("scheduleId") Long scheduleId,
        @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
        @Valid @RequestBody ScheduleDeadlineUpdateRequest request) {
    Long memberId = requireAuthenticatedMemberId(authenticatedMember);
    return ApiResponse.success(
            scheduleService.updateResponseDeadline(groupId, memberId, scheduleId, request));
}
```

- [ ] **Step 5: Add JPA integration coverage**

Extend `ScheduleUpdateIntegrationTest` with this persistence test:

```java
@Test
void changesAndRemovesOnlyResponseDeadline() {
    StudyGroup group = saveGroupWithLeader(27L, 1L);
    StudySchedule schedule =
            scheduleRepository.saveAndFlush(
                    StudySchedule.create(
                            group,
                            9L,
                            "기존 일정",
                            NOW.plusDays(3),
                            "기존 장소",
                            null,
                            "기존 내용",
                            "기존 준비물",
                            NOW.plusDays(1)));
    Long scheduleId = schedule.getId();
    LocalDateTime createdAt = schedule.getCreatedAt();

    ScheduleDeadlineUpdateRequest change = new ScheduleDeadlineUpdateRequest();
    change.setResponseDeadline(NOW.plusDays(2));
    service.updateResponseDeadline(group.getId(), 1L, scheduleId, change);
    entityManager.flush();
    entityManager.clear();

    StudySchedule changed = scheduleRepository.findById(scheduleId).orElseThrow();
    assertThat(changed.getResponseDeadline()).isEqualTo(NOW.plusDays(2));
    assertThat(changed.getCreatorId()).isEqualTo(9L);
    assertThat(changed.getCreatedAt()).isEqualTo(createdAt);
    assertThat(changed.getTitle()).isEqualTo("기존 일정");

    ScheduleDeadlineUpdateRequest remove = new ScheduleDeadlineUpdateRequest();
    remove.setResponseDeadline(null);
    service.updateResponseDeadline(group.getId(), 1L, scheduleId, remove);
    entityManager.flush();
    entityManager.clear();

    assertThat(scheduleRepository.findById(scheduleId).orElseThrow().getResponseDeadline())
            .isNull();
}
```

- [ ] **Step 6: Run the schedule package tests**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.*"
```

Expected: `BUILD SUCCESSFUL`; PATCH service, controller, DTO, entity and integration cases pass.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/mycom/myapp/schedule src/test/java/com/mycom/myapp/schedule
git commit -m "feat: 일정 응답 마감 변경 API 추가"
```

---

### Task 7: Part3 schedule deletion use case

**Files:**
- Modify: `src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java`
- Modify: `src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java`
- Modify: `src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java`
- Modify: `src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java`

**Interfaces:**
- Consumes: `AttendanceRecordLookup.existsByScheduleId(Long)`
- Consumes: `ActivityRecordLookup.existsByScheduleId(Long)`
- Produces: `void ScheduleService.delete(Long groupId, Long memberId, Long scheduleId)`
- Produces: `DELETE /api/groups/{groupId}/schedules/{scheduleId}`

- [ ] **Step 1: Add failing deletion service tests**

Add lookup mocks and constructor arguments to `ScheduleServiceTest`:

```java
private final AttendanceRecordLookup attendanceRecordLookup =
        mock(AttendanceRecordLookup.class);
private final ActivityRecordLookup activityRecordLookup = mock(ActivityRecordLookup.class);

private final ScheduleService service =
        new ScheduleService(
                groupRepository,
                memberRepository,
                scheduleRepository,
                attendanceRecordLookup,
                activityRecordLookup,
                clock);
```

Core success and dependency tests:

```java
@ParameterizedTest
@EnumSource(value = GroupRole.class, names = {"LEADER", "MANAGER"})
void deletesFutureScheduleWithoutProtectedRecords(GroupRole role) {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, role));
    StudySchedule schedule = schedule(group, 100L, NOW.plusHours(1), null);
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule));

    service.delete(10L, 1L, 100L);

    verify(scheduleRepository).delete(schedule);
    verify(scheduleRepository).flush();
}

@Test
void rejectsDeletionWhenAttendanceRecordExists() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule(group, 100L, NOW.plusHours(1), null)));
    when(attendanceRecordLookup.existsByScheduleId(100L)).thenReturn(true);

    assertDeleteError(ErrorCode.SCHEDULE_DELETE_NOT_ALLOWED);

    verify(scheduleRepository, never()).delete(any());
    verifyNoInteractions(activityRecordLookup);
}

@Test
void rejectsDeletionWhenActivityRecordExists() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule(group, 100L, NOW.plusHours(1), null)));
    when(activityRecordLookup.existsByScheduleId(100L)).thenReturn(true);

    assertDeleteError(ErrorCode.SCHEDULE_DELETE_NOT_ALLOWED);

    verify(scheduleRepository, never()).delete(any());
}
```

Add these remaining deletion tests:

```java
@Test
void rejectsDeletionForMissingGroup() {
    when(groupRepository.findById(10L)).thenReturn(Optional.empty());

    assertDeleteError(ErrorCode.GROUP_NOT_FOUND);
    verifyNoInteractions(memberRepository, scheduleRepository);
}

@Test
void rejectsDeletionForMissingMembership() {
    StudyGroup group = activeGroup();
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(memberRepository.findByStudyGroupIdAndUserId(10L, 1L))
            .thenReturn(Optional.empty());

    assertDeleteError(ErrorCode.GROUP_ACCESS_DENIED);
    verifyNoInteractions(scheduleRepository);
}

@Test
void rejectsDeletionForWithdrawnMember() {
    StudyGroup group = activeGroup();
    GroupMember member = GroupMember.join(group, 1L, GroupRole.LEADER);
    member.withdraw();
    allow(group, member);

    assertDeleteError(ErrorCode.WITHDRAWN_GROUP_MEMBER);
    verifyNoInteractions(scheduleRepository);
}

@Test
void rejectsDeletionForEndedGroup() {
    StudyGroup group = activeGroup();
    group.end();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));

    assertDeleteError(ErrorCode.GROUP_ENDED);
    verifyNoInteractions(scheduleRepository);
}

@Test
void rejectsDeletionForRegularMember() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));

    assertDeleteError(ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN);
    verifyNoInteractions(scheduleRepository);
}

@Test
void reportsMissingScheduleBeforeTimeAndDependencyChecks() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.empty());

    assertDeleteError(ErrorCode.SCHEDULE_NOT_FOUND);
    verifyNoInteractions(attendanceRecordLookup, activityRecordLookup);
}

@ParameterizedTest
@MethodSource("startedScheduleTimes")
void rejectsDeletionAtOrBeforeScheduleTime(LocalDateTime scheduledAt) {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule(group, 100L, scheduledAt, null)));

    assertDeleteError(ErrorCode.SCHEDULE_DELETE_NOT_ALLOWED);
    verifyNoInteractions(attendanceRecordLookup, activityRecordLookup);
}

@Test
void mapsForeignKeyRaceToDeleteNotAllowed() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    StudySchedule schedule = schedule(group, 100L, NOW.plusHours(1), null);
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule));
    doThrow(new DataIntegrityViolationException("protected schedule"))
            .when(scheduleRepository)
            .flush();

    assertDeleteError(ErrorCode.SCHEDULE_DELETE_NOT_ALLOWED);
}

private void assertDeleteError(ErrorCode expected) {
    assertThatThrownBy(() -> service.delete(10L, 1L, 100L))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
}
```

- [ ] **Step 2: Run the service test and confirm constructor/method failures**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleServiceTest"
```

Expected: compilation fails because the constructor dependencies and `delete` method do not exist.

- [ ] **Step 3: Add lookup dependencies and delete implementation**

Extend the `ScheduleService` constructor with:

```java
AttendanceRecordLookup attendanceRecordLookup,
ActivityRecordLookup activityRecordLookup,
```

Store both as final fields before `clock`. Add:

```java
@Transactional
public void delete(Long groupId, Long memberId, Long scheduleId) {
    StudyGroup group = getGroup(groupId);
    GroupMember member = getActiveMember(groupId, memberId);
    validateScheduleManagement(group, member);
    StudySchedule schedule =
            scheduleRepository
                    .findByIdAndStudyGroupId(scheduleId, groupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    LocalDateTime now = LocalDateTime.now(clock);
    if (!schedule.getScheduledAt().isAfter(now)) {
        throw new BusinessException(ErrorCode.SCHEDULE_DELETE_NOT_ALLOWED);
    }
    if (attendanceRecordLookup.existsByScheduleId(scheduleId)
            || activityRecordLookup.existsByScheduleId(scheduleId)) {
        throw new BusinessException(ErrorCode.SCHEDULE_DELETE_NOT_ALLOWED);
    }
    try {
        scheduleRepository.delete(schedule);
        scheduleRepository.flush();
    } catch (DataIntegrityViolationException exception) {
        throw new BusinessException(ErrorCode.SCHEDULE_DELETE_NOT_ALLOWED);
    }
}
```

- [ ] **Step 4: Add controller test and endpoint**

Controller assertion:

```java
mockMvc.perform(delete("/api/groups/10/schedules/100"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

verify(scheduleService).delete(10L, principal.id(), 100L);
```

Endpoint:

```java
@DeleteMapping("/{scheduleId}")
public ResponseEntity<Void> delete(
        @PathVariable("groupId") Long groupId,
        @PathVariable("scheduleId") Long scheduleId,
        @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
    Long memberId = requireAuthenticatedMemberId(authenticatedMember);
    scheduleService.delete(groupId, memberId, scheduleId);
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 5: Run the schedule unit/controller tests**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleServiceTest" --tests "com.mycom.myapp.schedule.controller.ScheduleControllerTest"
```

Expected: `BUILD SUCCESSFUL`; validation order, time boundary, protected-record and `204` cases pass.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java
git commit -m "feat: 미래 일정 삭제 API 추가"
```

---

### Task 8: FK integration contract and persistence verification

**Files:**
- Create: `src/test/resources/sql/schedule-deletion-fk.sql`
- Create: `src/test/java/com/mycom/myapp/schedule/service/ScheduleDeletionIntegrationTest.java`
- Create: `docs/part3-group/schedule-fk-verification.sql`

**Interfaces:**
- Consumes: `ScheduleService.delete(Long, Long, Long)`
- Verifies: attendance response `CASCADE`
- Verifies: attendance record and activity record `RESTRICT`
- Produces: exact MySQL verification query for the DB owner

- [ ] **Step 1: Add the H2 FK contract script**

```sql
ALTER TABLE attendance_responses
    ADD CONSTRAINT fk_attendance_responses_schedule
    FOREIGN KEY (schedule_id) REFERENCES study_schedules(id)
    ON DELETE CASCADE;

ALTER TABLE attendance_records
    ADD CONSTRAINT fk_attendance_records_schedule
    FOREIGN KEY (schedule_id) REFERENCES study_schedules(id)
    ON DELETE RESTRICT;

ALTER TABLE activity_records
    ADD CONSTRAINT fk_activity_records_schedule
    FOREIGN KEY (schedule_id) REFERENCES study_schedules(id)
    ON DELETE RESTRICT;
```

- [ ] **Step 2: Write the integration test**

Use a dedicated `@SpringBootTest` H2 database, `@Sql("/sql/schedule-deletion-fk.sql")` with
`BEFORE_TEST_CLASS`, fixed `scheduleClock`, and transactions. Implement these three tests:

```java
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

    assertThatThrownBy(
                    () -> service.delete(fixture.group().getId(), 1L, fixture.schedule().getId()))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.SCHEDULE_DELETE_NOT_ALLOWED));
    assertThat(scheduleRepository.findById(fixture.schedule().getId())).isPresent();
}

@Test
void activityRecordPreventsScheduleDeletion() {
    Fixture fixture = saveFutureScheduleWithLeader();
    activityRecordRepository.saveAndFlush(ActivityRecord.forSchedule(fixture.schedule().getId()));

    assertThatThrownBy(
                    () -> service.delete(fixture.group().getId(), 1L, fixture.schedule().getId()))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.SCHEDULE_DELETE_NOT_ALLOWED));
}
```

Use this exact fixture record and factory in the integration test:

```java
private record Fixture(StudyGroup group, StudySchedule schedule) {}

private Fixture saveFutureScheduleWithLeader() {
    StudyGroup group =
            groupRepository.saveAndFlush(
                    StudyGroup.create(nextPostId++, "삭제 통합 테스트 그룹"));
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
```

Declare `private long nextPostId = 100L;` in the test class so each fixture satisfies the unique `post_id`
constraint.

- [ ] **Step 3: Run the integration test**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleDeletionIntegrationTest"
```

Expected: `BUILD SUCCESSFUL`; one CASCADE and two protected-delete cases pass.

- [ ] **Step 4: Add the production verification SQL**

```sql
SELECT
    rc.TABLE_NAME,
    rc.CONSTRAINT_NAME,
    rc.DELETE_RULE,
    kcu.COLUMN_NAME,
    kcu.REFERENCED_TABLE_NAME,
    kcu.REFERENCED_COLUMN_NAME
FROM information_schema.REFERENTIAL_CONSTRAINTS rc
JOIN information_schema.KEY_COLUMN_USAGE kcu
  ON kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
 AND kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
 AND kcu.TABLE_NAME = rc.TABLE_NAME
WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
  AND kcu.REFERENCED_TABLE_NAME = 'study_schedules'
  AND kcu.COLUMN_NAME = 'schedule_id'
ORDER BY rc.TABLE_NAME;
```

Expected production result:

```text
activity_records      RESTRICT
attendance_records    RESTRICT
attendance_responses  CASCADE
```

Because the repository has no Flyway or Liquibase dependency, the DB owner must apply the reviewed DDL
outside the application deployment and run this query before enabling the DELETE endpoint. Do not add an
untracked startup schema mutation.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/sql/schedule-deletion-fk.sql src/test/java/com/mycom/myapp/schedule/service/ScheduleDeletionIntegrationTest.java docs/part3-group/schedule-fk-verification.sql
git commit -m "test: 일정 삭제 FK 정책 통합 검증 추가"
```

---

### Task 9: API synchronization and final verification

**Files:**
- Modify: `docs/part3-group/api.md`
- Modify: `docs/part3-group/context.md`
- Verify: `docs/part3-group/erd.md`
- Verify: `docs/part3-group/development-guide.md`
- Verify: `docs/part3-group/schedule-deletion-deadline-design.md`

**Interfaces:**
- Consumes: implemented DELETE, PATCH, Part4 deadline and FK behavior
- Produces: implementation-synchronized documentation and CI evidence

- [ ] **Step 1: Update Part3 API documentation**

Add both endpoints to the endpoint table and document exact request, response, validation order and error
tables from the approved design. Mark `responseDeadline` as preserved by the existing PUT and mutable only by
the new PATCH.

- [ ] **Step 2: Update the session context**

Record the branch, latest commit, implemented features, Part4/activity/DB changes, test commands and any DB
deployment prerequisite that remains external to the repository.

- [ ] **Step 3: Run focused package tests**

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.*" --tests "com.mycom.myapp.attendance.*" --tests "com.mycom.myapp.activity.*"
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [ ] **Step 4: Run full verification**

```powershell
.\gradlew.bat test
.\gradlew.bat spotlessCheck
git diff --check
```

Expected: both Gradle commands print `BUILD SUCCESSFUL`; `git diff --check` prints no output.

- [ ] **Step 5: Review the final change boundary**

```powershell
git status --short
git diff --stat origin/develop...HEAD
git diff --name-only origin/develop...HEAD
```

Expected: only schedule/study, their tests, the agreed attendance/activity adapters, common error codes, SQL
verification artifacts and related documentation appear.

- [ ] **Step 6: Commit documentation**

```powershell
git add docs/part3-group/api.md docs/part3-group/context.md docs/part4-attendance/api.md
git commit -m "docs: 일정 삭제와 응답 마감 API 반영"
```

## Execution Order and Review Gates

1. Tasks 1–3 establish shared contracts and must compile before any consumer work.
2. Task 4 is a Part4-owned PR or independently reviewable commit.
3. Tasks 5–7 are Part3-owned commits and must not absorb unrelated Part4 refactoring.
4. Task 8 requires DB-owner approval of production FK rules before deployment.
5. Task 9 runs only after every prior task is integrated on the working branch.

At each gate, stop if the owning part rejects an interface or FK rule. Revise the design and plan before
continuing rather than coupling Part3 directly to another part's Repository.
