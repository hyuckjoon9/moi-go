# Part3 Schedule Creation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 활성 그룹의 `LEADER` 또는 `MANAGER`가 장소 미정 상태를 포함한 미래 일정을 생성하고 저장된 일정 전체를 `201 Created`로 받게 한다.

**Architecture:** `ScheduleController`가 인증 사용자와 요청을 받아 트랜잭션 `ScheduleService`에 위임한다. Service는 그룹·그룹원·역할·시간을 순서대로 검증하고 `StudySchedule`을 저장하며, Entity는 JPA 매핑과 `responseDeadline <= scheduledAt` 불변식을 보장한다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Web, Spring Data JPA, Spring Security, Jakarta Validation, JUnit 5, AssertJ, Mockito, MockMvc.

## Global Constraints

- 기준 문서는 `docs/part3-group/api.md`, `erd.md`, `schedule-create-design.md`다.
- 엔드포인트는 `POST /api/groups/{groupId}/schedules`, 성공 상태는 `201 Created`다.
- `location`과 `onlineLink`는 모두 선택값이고 둘 다 `null`이면 장소 미정이다.
- `onlineLink`는 URL 형식을 강제하지 않는 최대 500자의 일반 문자열이다.
- `content`와 `materials`는 각각 최대 5,000자다.
- `scheduledAt > now`, `responseDeadline == null || now < responseDeadline <= scheduledAt`을 적용한다.
- 활성 `ACTIVE` 그룹의 `LEADER`와 `MANAGER`만 생성할 수 있다.
- 검증 순서는 그룹 존재, 그룹원 기록, 탈퇴 상태, 그룹 상태, 역할, 시간 순서다.
- `global/exception/ErrorCode.java`에는 `GROUP_ENDED`, `SCHEDULE_MANAGEMENT_FORBIDDEN`, `INVALID_SCHEDULE_TIME`만 추가한다.
- `ApiResponse`, `GlobalExceptionHandler`, `SecurityConfig`, JWT와 다른 파트 코드는 수정하지 않는다.
- 모든 운영 코드보다 해당 동작의 실패 테스트를 먼저 작성하고 예상한 이유로 실패하는지 확인한다.

---

## File Structure

| 파일 | 책임 |
| --- | --- |
| `schedule/dto/request/ScheduleCreateRequest.java` | 문자열 정규화와 형식·길이 검증 |
| `schedule/entity/StudySchedule.java` | 일정 JPA 매핑과 시각 관계 불변식 |
| `schedule/repository/StudyScheduleRepository.java` | 일정 저장 및 그룹별 시간순 조회 |
| `schedule/config/ScheduleTimeConfig.java` | 운영 환경의 `scheduleClock` 제공 |
| `schedule/dto/response/ScheduleResponse.java` | 저장된 일정 전체를 API 응답으로 변환 |
| `schedule/service/ScheduleService.java` | 권한·그룹 상태·현재 시각 검증과 저장 트랜잭션 |
| `schedule/controller/ScheduleController.java` | HTTP 입력, 인증 사용자 전달, `201` 응답 |
| `global/exception/ErrorCode.java` | 합의된 세 오류의 HTTP 상태와 메시지 |

---

### Task 1: 일정 생성 요청 정규화와 검증

**Files:**
- Modify: `src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleCreateRequest.java`
- Create: `src/test/java/com/mycom/myapp/schedule/dto/request/ScheduleCreateRequestTest.java`

**Interfaces:**
- Consumes: JSON 필드 `title`, `scheduledAt`, `location`, `onlineLink`, `content`, `materials`, `responseDeadline`
- Produces: 같은 이름의 record 접근자와 정규화된 값

- [ ] **Step 1: 실패하는 Request DTO 테스트 작성**

```java
package com.mycom.myapp.schedule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 25, 19, 0);

    @Test
    void normalizesRequiredAndOptionalStrings() {
        ScheduleCreateRequest request =
                new ScheduleCreateRequest(
                        "  3주차 스터디  ", scheduledAt, "   ", " Zoom 123 ", "  내용  ", "", null);

        assertThat(request.title()).isEqualTo("3주차 스터디");
        assertThat(request.location()).isNull();
        assertThat(request.onlineLink()).isEqualTo("Zoom 123");
        assertThat(request.content()).isEqualTo("내용");
        assertThat(request.materials()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankTitleAndMissingScheduledAt() {
        ScheduleCreateRequest request =
                new ScheduleCreateRequest("   ", null, null, null, null, null, null);

        assertThat(propertiesOf(validator.validate(request))).containsExactlyInAnyOrder("title", "scheduledAt");
    }

    @Test
    void rejectsValuesBeyondDocumentedLengths() {
        ScheduleCreateRequest request =
                new ScheduleCreateRequest(
                        "t".repeat(101),
                        scheduledAt,
                        "l".repeat(256),
                        "o".repeat(501),
                        "c".repeat(5001),
                        "m".repeat(5001),
                        null);

        assertThat(propertiesOf(validator.validate(request)))
                .containsExactlyInAnyOrder("title", "location", "onlineLink", "content", "materials");
    }

    private Set<String> propertiesOf(Set<ConstraintViolation<ScheduleCreateRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
```

- [ ] **Step 2: 테스트가 DTO 부재 동작 때문에 실패하는지 확인**

Run:

```powershell
.\gradlew.bat test --tests com.mycom.myapp.schedule.dto.request.ScheduleCreateRequestTest
```

Expected: record 생성자와 접근자가 없어 `compileTestJava`가 실패한다.

- [ ] **Step 3: 최소 Request DTO 구현**

```java
package com.mycom.myapp.schedule.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ScheduleCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull LocalDateTime scheduledAt,
        @Size(max = 255) String location,
        @Size(max = 500) String onlineLink,
        @Size(max = 5000) String content,
        @Size(max = 5000) String materials,
        LocalDateTime responseDeadline) {

    public ScheduleCreateRequest {
        title = normalize(title);
        location = normalize(location);
        onlineLink = normalize(onlineLink);
        content = normalize(content);
        materials = normalize(materials);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
```

- [ ] **Step 4: Request DTO 테스트 통과 확인**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: 요청 계약 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleCreateRequest.java src/test/java/com/mycom/myapp/schedule/dto/request/ScheduleCreateRequestTest.java
git commit -m "feat: 일정 생성 요청 검증 추가"
```

---

### Task 2: 일정 Entity와 Repository 영속성

**Files:**
- Modify: `src/main/java/com/mycom/myapp/schedule/entity/StudySchedule.java`
- Modify: `src/main/java/com/mycom/myapp/schedule/repository/StudyScheduleRepository.java`
- Create: `src/test/java/com/mycom/myapp/schedule/entity/StudyScheduleTest.java`
- Create: `src/test/java/com/mycom/myapp/schedule/repository/StudyScheduleRepositoryTest.java`

**Interfaces:**
- Consumes: `StudyGroup`, `creatorId`, 정규화된 일정 입력
- Produces: `StudySchedule.create(...)`, getter, `findAllByStudyGroupIdOrderByScheduledAtAsc(Long)`

- [ ] **Step 1: Entity 불변식 실패 테스트 작성**

```java
package com.mycom.myapp.schedule.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.mycom.myapp.study.entity.StudyGroup;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StudyScheduleTest {

    private final StudyGroup group = StudyGroup.create(10L, "알고리즘 스터디");
    private final LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 25, 19, 0);

    @Test
    void createsScheduleWithoutLocationOrLink() {
        StudySchedule schedule = StudySchedule.create(group, 1L, "3주차", scheduledAt, null, null, null, null, null);

        assertThat(schedule.getStudyGroup()).isSameAs(group);
        assertThat(schedule.getCreatorId()).isEqualTo(1L);
        assertThat(schedule.getLocation()).isNull();
        assertThat(schedule.getOnlineLink()).isNull();
    }

    @Test
    void allowsDeadlineEqualToScheduledAt() {
        StudySchedule schedule = StudySchedule.create(group, 1L, "3주차", scheduledAt, null, null, null, null, scheduledAt);

        assertThat(schedule.getResponseDeadline()).isEqualTo(scheduledAt);
    }

    @Test
    void rejectsDeadlineAfterScheduledAt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StudySchedule.create(group, 1L, "3주차", scheduledAt, null, null, null, null, scheduledAt.plusMinutes(1)));
    }
}
```

- [ ] **Step 2: Entity 테스트의 예상 실패 확인**

```powershell
.\gradlew.bat test --tests com.mycom.myapp.schedule.entity.StudyScheduleTest
```

Expected: `StudySchedule.create`와 getter가 없어 컴파일이 실패한다.

- [ ] **Step 3: 스키마와 일치하는 Entity 구현**

Replace `StudySchedule.java` with the following implementation:

```java
package com.mycom.myapp.schedule.entity;

import com.mycom.myapp.study.entity.StudyGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "study_schedules",
        indexes = {
            @Index(name = "idx_study_schedules_group_scheduled", columnList = "group_id,scheduled_at"),
            @Index(name = "idx_study_schedules_creator", columnList = "creator_id")
        })
public class StudySchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private StudyGroup studyGroup;
    @Column(name = "creator_id", nullable = false) private Long creatorId;
    @Column(nullable = false, length = 100) private String title;
    @Column(name = "scheduled_at", nullable = false) private LocalDateTime scheduledAt;
    @Column(length = 255) private String location;
    @Column(name = "online_link", length = 500) private String onlineLink;
    @Column(columnDefinition = "text") private String content;
    @Column(columnDefinition = "text") private String materials;
    @Column(name = "response_deadline") private LocalDateTime responseDeadline;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected StudySchedule() {}

    private StudySchedule(
            StudyGroup studyGroup,
            Long creatorId,
            String title,
            LocalDateTime scheduledAt,
            String location,
            String onlineLink,
            String content,
            String materials,
            LocalDateTime responseDeadline) {
        this.studyGroup = studyGroup;
        this.creatorId = creatorId;
        this.title = title;
        this.scheduledAt = scheduledAt;
        this.location = location;
        this.onlineLink = onlineLink;
        this.content = content;
        this.materials = materials;
        this.responseDeadline = responseDeadline;
    }

    public static StudySchedule create(
            StudyGroup studyGroup, Long creatorId, String title, LocalDateTime scheduledAt,
            String location, String onlineLink, String content, String materials,
            LocalDateTime responseDeadline) {
        if (studyGroup == null || creatorId == null || title == null || title.isBlank() || scheduledAt == null) {
            throw new IllegalArgumentException("일정 필수값이 누락되었습니다.");
        }
        if (responseDeadline != null && responseDeadline.isAfter(scheduledAt)) {
            throw new IllegalArgumentException("응답 마감 시간은 일정 시간보다 늦을 수 없습니다.");
        }
        return new StudySchedule(
                studyGroup,
                creatorId,
                title,
                scheduledAt,
                location,
                onlineLink,
                content,
                materials,
                responseDeadline);
    }

    @PrePersist
    private void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = createdAt;
    }

    public Long getId() { return id; }
    public StudyGroup getStudyGroup() { return studyGroup; }
    public Long getCreatorId() { return creatorId; }
    public String getTitle() { return title; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public String getLocation() { return location; }
    public String getOnlineLink() { return onlineLink; }
    public String getContent() { return content; }
    public String getMaterials() { return materials; }
    public LocalDateTime getResponseDeadline() { return responseDeadline; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 4: Entity 테스트 통과 확인**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Repository 실패 테스트 작성**

Create `StudyScheduleRepositoryTest` with the following two tests:

```java
package com.mycom.myapp.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class StudyScheduleRepositoryTest {

    @Autowired private StudyGroupRepository groupRepository;
    @Autowired private StudyScheduleRepository scheduleRepository;

    @Test
    void storesLocationUndecidedScheduleAndInitializesTimestamps() {
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));
        StudySchedule schedule =
                StudySchedule.create(
                        group,
                        1L,
                        "장소 미정 일정",
                        LocalDateTime.of(2026, 7, 25, 19, 0),
                        null,
                        null,
                        null,
                        null,
                        null);

        StudySchedule saved = scheduleRepository.saveAndFlush(schedule);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLocation()).isNull();
        assertThat(saved.getOnlineLink()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void findsGroupSchedulesByScheduledAtAscending() {
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));
        scheduleRepository.save(
                schedule(group, "세 번째 일정", LocalDateTime.of(2026, 7, 27, 19, 0)));
        scheduleRepository.save(
                schedule(group, "첫 일정", LocalDateTime.of(2026, 7, 25, 19, 0)));
        scheduleRepository.saveAndFlush(
                schedule(group, "두 번째 일정", LocalDateTime.of(2026, 7, 26, 19, 0)));

        assertThat(scheduleRepository.findAllByStudyGroupIdOrderByScheduledAtAsc(group.getId()))
                .extracting(StudySchedule::getTitle)
                .containsExactly("첫 일정", "두 번째 일정", "세 번째 일정");
    }

    private StudySchedule schedule(StudyGroup group, String title, LocalDateTime scheduledAt) {
        return StudySchedule.create(
                group, 1L, title, scheduledAt, null, null, null, null, null);
    }
}
```

- [ ] **Step 6: Repository 테스트의 예상 실패 확인**

```powershell
.\gradlew.bat test --tests com.mycom.myapp.schedule.repository.StudyScheduleRepositoryTest
```

Expected: Repository가 `JpaRepository`를 상속하지 않고 조회 메서드가 없어 컴파일이 실패한다.

- [ ] **Step 7: Repository 구현**

```java
package com.mycom.myapp.schedule.repository;

import com.mycom.myapp.schedule.entity.StudySchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyScheduleRepository extends JpaRepository<StudySchedule, Long> {
    List<StudySchedule> findAllByStudyGroupIdOrderByScheduledAtAsc(Long groupId);
}
```

- [ ] **Step 8: Entity와 Repository 테스트 통과 확인**

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.entity.*" --tests "com.mycom.myapp.schedule.repository.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: 영속성 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/entity/StudySchedule.java src/main/java/com/mycom/myapp/schedule/repository/StudyScheduleRepository.java src/test/java/com/mycom/myapp/schedule/entity/StudyScheduleTest.java src/test/java/com/mycom/myapp/schedule/repository/StudyScheduleRepositoryTest.java
git commit -m "feat: 일정 영속성 기반 추가"
```

---

### Task 3: 일정 생성 Service, 응답과 오류

**Files:**
- Create: `src/main/java/com/mycom/myapp/schedule/config/ScheduleTimeConfig.java`
- Modify: `src/main/java/com/mycom/myapp/schedule/dto/response/ScheduleResponse.java`
- Modify: `src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java`
- Modify: `src/main/java/com/mycom/myapp/global/exception/ErrorCode.java`
- Create: `src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java`

**Interfaces:**
- Consumes: `ScheduleService.create(Long groupId, Long memberId, ScheduleCreateRequest request)`
- Produces: `ScheduleResponse`, 합의된 세 `ErrorCode`, `scheduleClock` bean

- [ ] **Step 1: Service 실패 테스트 작성**

Use Mockito repositories and a fixed clock:

```java
private static final Instant NOW = Instant.parse("2026-07-20T03:00:00Z");
private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
private final Clock clock = Clock.fixed(NOW, ZONE);
private final StudyGroupRepository groupRepository = mock(StudyGroupRepository.class);
private final GroupMemberRepository memberRepository = mock(GroupMemberRepository.class);
private final StudyScheduleRepository scheduleRepository = mock(StudyScheduleRepository.class);
private final ScheduleService service = new ScheduleService(groupRepository, memberRepository, scheduleRepository, clock);
```

Use the following test structure. Keep each failure as a separate `@Test` so the validation order remains visible:

```java
class ScheduleServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 20, 12, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    private final StudyGroupRepository groupRepository = mock(StudyGroupRepository.class);
    private final GroupMemberRepository memberRepository = mock(GroupMemberRepository.class);
    private final StudyScheduleRepository scheduleRepository = mock(StudyScheduleRepository.class);
    private final ScheduleService service =
            new ScheduleService(groupRepository, memberRepository, scheduleRepository, clock);

    @ParameterizedTest
    @EnumSource(value = GroupRole.class, names = {"LEADER", "MANAGER"})
    void createsScheduleForManagementRole(GroupRole role) {
        StudyGroup group = activeGroup();
        GroupMember member = GroupMember.join(group, 1L, role);
        allow(group, member);
        when(scheduleRepository.save(any(StudySchedule.class)))
                .thenAnswer(
                        invocation -> {
                            StudySchedule schedule = invocation.getArgument(0);
                            ReflectionTestUtils.setField(schedule, "id", 100L);
                            ReflectionTestUtils.setField(schedule, "createdAt", NOW);
                            ReflectionTestUtils.setField(schedule, "updatedAt", NOW);
                            return schedule;
                        });

        ScheduleResponse response = service.create(10L, 1L, validRequest());

        assertThat(response.scheduleId()).isEqualTo(100L);
        assertThat(response.groupId()).isEqualTo(10L);
        assertThat(response.creatorId()).isEqualTo(1L);
        assertThat(response.location()).isNull();
        assertThat(response.onlineLink()).isNull();
        verify(scheduleRepository).save(any(StudySchedule.class));
    }

    @Test
    void rejectsMissingGroup() {
        when(groupRepository.findById(10L)).thenReturn(Optional.empty());
        assertError(ErrorCode.GROUP_NOT_FOUND, validRequest());
    }

    @Test
    void rejectsMissingMembership() {
        StudyGroup group = activeGroup();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(memberRepository.findByStudyGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        assertError(ErrorCode.GROUP_ACCESS_DENIED, validRequest());
    }

    @Test
    void rejectsWithdrawnMember() {
        StudyGroup group = activeGroup();
        GroupMember member = GroupMember.join(group, 1L, GroupRole.LEADER);
        member.withdraw();
        allow(group, member);
        assertError(ErrorCode.WITHDRAWN_GROUP_MEMBER, validRequest());
    }

    @Test
    void rejectsEndedGroupBeforeRoleCheck() {
        StudyGroup group = activeGroup();
        group.end();
        allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));
        assertError(ErrorCode.GROUP_ENDED, validRequest());
    }

    @Test
    void rejectsActiveMemberWithoutManagementRole() {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));
        assertError(ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN, validRequest());
    }

    @ParameterizedTest
    @MethodSource("invalidTimeRequests")
    void rejectsInvalidScheduleTimes(ScheduleCreateRequest request) {
        StudyGroup group = activeGroup();
        allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
        assertError(ErrorCode.INVALID_SCHEDULE_TIME, request);
    }

    private static Stream<ScheduleCreateRequest> invalidTimeRequests() {
        return Stream.of(
                request(NOW, null),
                request(NOW.minusSeconds(1), null),
                request(NOW.plusHours(2), NOW),
                request(NOW.plusHours(2), NOW.plusHours(3)));
    }

    private void assertError(ErrorCode errorCode, ScheduleCreateRequest request) {
        assertThatThrownBy(() -> service.create(10L, 1L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
        verify(scheduleRepository, never()).save(any());
    }

    private void allow(StudyGroup group, GroupMember member) {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(memberRepository.findByStudyGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(member));
    }

    private StudyGroup activeGroup() {
        StudyGroup group = StudyGroup.create(25L, "알고리즘 스터디");
        ReflectionTestUtils.setField(group, "id", 10L);
        return group;
    }

    private ScheduleCreateRequest validRequest() {
        return request(NOW.plusHours(2), NOW.plusHours(1));
    }

    private static ScheduleCreateRequest request(
            LocalDateTime scheduledAt, LocalDateTime responseDeadline) {
        return new ScheduleCreateRequest(
                "3주차 스터디",
                scheduledAt,
                null,
                null,
                "3장 문제 풀이",
                "교재와 노트북",
                responseDeadline);
    }
}
```

Add this boundary success test to the same class:

```java
@Test
void allowsDeadlineEqualToScheduledAt() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.save(any(StudySchedule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    LocalDateTime scheduledAt = NOW.plusHours(2);

    service.create(10L, 1L, request(scheduledAt, scheduledAt));

    verify(scheduleRepository).save(any(StudySchedule.class));
}
```

Use `ReflectionTestUtils` only for generated IDs and persisted timestamps; do not add test-only production setters.

- [ ] **Step 2: Service 테스트의 예상 실패 확인**

```powershell
.\gradlew.bat test --tests com.mycom.myapp.schedule.service.ScheduleServiceTest
```

Expected: 새 오류, Service 생성자·메서드와 Response API가 없어 컴파일이 실패한다.

- [ ] **Step 3: 합의된 오류 세 개 추가**

Add immediately before `INTERNAL_SERVER_ERROR`:

```java
GROUP_ENDED(HttpStatus.CONFLICT, "종료된 그룹에서는 일정을 생성할 수 없습니다."),
SCHEDULE_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "일정을 관리할 권한이 없습니다."),
INVALID_SCHEDULE_TIME(HttpStatus.BAD_REQUEST, "일정 또는 응답 마감 시간이 올바르지 않습니다."),
```

- [ ] **Step 4: ScheduleResponse 구현**

```java
public record ScheduleResponse(
        Long scheduleId,
        Long groupId,
        Long creatorId,
        String title,
        LocalDateTime scheduledAt,
        String location,
        String onlineLink,
        String content,
        String materials,
        LocalDateTime responseDeadline,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ScheduleResponse from(StudySchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(), schedule.getStudyGroup().getId(), schedule.getCreatorId(),
                schedule.getTitle(), schedule.getScheduledAt(), schedule.getLocation(),
                schedule.getOnlineLink(), schedule.getContent(), schedule.getMaterials(),
                schedule.getResponseDeadline(), schedule.getCreatedAt(), schedule.getUpdatedAt());
    }
}
```

- [ ] **Step 5: 운영 Clock bean 구현**

```java
@Configuration
public class ScheduleTimeConfig {
    @Bean("scheduleClock")
    public Clock scheduleClock() {
        return Clock.systemDefaultZone();
    }
}
```

- [ ] **Step 6: ScheduleService 최소 구현**

```java
@Service
public class ScheduleService {
    private final StudyGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final StudyScheduleRepository scheduleRepository;
    private final Clock clock;

    public ScheduleService(
            StudyGroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            StudyScheduleRepository scheduleRepository,
            @Qualifier("scheduleClock") Clock clock) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.scheduleRepository = scheduleRepository;
        this.clock = clock;
    }

    @Transactional
    public ScheduleResponse create(Long groupId, Long memberId, ScheduleCreateRequest request) {
        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        GroupMember member = memberRepository.findByStudyGroupIdAndUserId(groupId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));
        if (member.getStatus() == GroupMemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_GROUP_MEMBER);
        }
        if (group.getStatus() == GroupStatus.ENDED) {
            throw new BusinessException(ErrorCode.GROUP_ENDED);
        }
        if (member.getRole() != GroupRole.LEADER && member.getRole() != GroupRole.MANAGER) {
            throw new BusinessException(ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime deadline = request.responseDeadline();
        if (!request.scheduledAt().isAfter(now)
                || (deadline != null
                        && (!deadline.isAfter(now) || deadline.isAfter(request.scheduledAt())))) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_TIME);
        }

        StudySchedule schedule = StudySchedule.create(
                group, memberId, request.title(), request.scheduledAt(), request.location(),
                request.onlineLink(), request.content(), request.materials(), deadline);
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }
}
```

- [ ] **Step 7: Service 테스트 통과 확인**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Service와 공통 오류 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/config/ScheduleTimeConfig.java src/main/java/com/mycom/myapp/schedule/dto/response/ScheduleResponse.java src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java src/main/java/com/mycom/myapp/global/exception/ErrorCode.java src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java
git commit -m "feat: 권한 기반 일정 생성 서비스 추가"
```

---

### Task 4: 일정 생성 HTTP API

**Files:**
- Modify: `src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java`
- Create: `src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java`

**Interfaces:**
- Consumes: `POST /api/groups/{groupId}/schedules`, `AuthenticatedMember`, valid JSON
- Produces: `ResponseEntity<ApiResponse<ScheduleResponse>>` with `201 Created`

- [ ] **Step 1: Controller 실패 테스트 작성**

Build standalone `MockMvc` in `@BeforeEach` with this resolver and validator:

```java
private final ScheduleService scheduleService = mock(ScheduleService.class);
private final AuthenticatedMember principal =
        new AuthenticatedMember(1L, "leader@example.com", MemberRole.USER);
private final ScheduleCreateRequest validRequest =
        new ScheduleCreateRequest(
                "3주차 스터디",
                LocalDateTime.of(2026, 7, 25, 19, 0),
                null,
                null,
                "3장 문제 풀이",
                "교재와 노트북",
                LocalDateTime.of(2026, 7, 24, 18, 0));
private final String validRequestJson =
        """
        {
          "title":"3주차 스터디",
          "scheduledAt":"2026-07-25T19:00:00",
          "location":null,
          "onlineLink":null,
          "content":"3장 문제 풀이",
          "materials":"교재와 노트북",
          "responseDeadline":"2026-07-24T18:00:00"
        }
        """;
private MockMvc mockMvc;

@BeforeEach
void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    HandlerMethodArgumentResolver principalResolver =
            new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                }

                @Override
                public Object resolveArgument(
                        MethodParameter parameter,
                        ModelAndViewContainer mavContainer,
                        NativeWebRequest webRequest,
                        WebDataBinderFactory binderFactory) {
                    return principal;
                }
            };
    mockMvc =
            MockMvcBuilders.standaloneSetup(new ScheduleController(scheduleService))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(principalResolver)
                    .setValidator(validator)
                    .build();
}
```

Add this success test:

```java
@Test
void createsScheduleAndReturnsFullResponse() throws Exception {
    ScheduleResponse response =
            new ScheduleResponse(
                    100L,
                    10L,
                    1L,
                    "3주차 스터디",
                    LocalDateTime.of(2026, 7, 25, 19, 0),
                    null,
                    null,
                    "3장 문제 풀이",
                    "교재와 노트북",
                    LocalDateTime.of(2026, 7, 24, 18, 0),
                    LocalDateTime.of(2026, 7, 20, 12, 0),
                    LocalDateTime.of(2026, 7, 20, 12, 0));
    when(scheduleService.create(eq(10L), eq(1L), any(ScheduleCreateRequest.class)))
            .thenReturn(response);

    mockMvc.perform(
                    post("/api/groups/{groupId}/schedules", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.scheduleId").value(100))
            .andExpect(jsonPath("$.data.groupId").value(10))
            .andExpect(jsonPath("$.data.creatorId").value(1))
            .andExpect(jsonPath("$.data.location").doesNotExist())
            .andExpect(jsonPath("$.message").doesNotExist());
    verify(scheduleService).create(eq(10L), eq(1L), any(ScheduleCreateRequest.class));
}
```

Add the following validation and exception patterns:

```java
@Test
void rejectsBlankTitleAndMissingScheduledAt() throws Exception {
    mockMvc.perform(
                    post("/api/groups/{groupId}/schedules", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"   ","scheduledAt":null}
                                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    verifyNoInteractions(scheduleService);
}

@ParameterizedTest
@MethodSource("businessErrors")
void mapsBusinessErrors(ErrorCode errorCode) throws Exception {
    when(scheduleService.create(eq(10L), eq(1L), any(ScheduleCreateRequest.class)))
            .thenThrow(new BusinessException(errorCode));

    mockMvc.perform(
                    post("/api/groups/{groupId}/schedules", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
            .andExpect(status().is(errorCode.getStatus().value()))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
}

private static Stream<ErrorCode> businessErrors() {
    return Stream.of(
            ErrorCode.GROUP_NOT_FOUND,
            ErrorCode.GROUP_ACCESS_DENIED,
            ErrorCode.WITHDRAWN_GROUP_MEMBER,
            ErrorCode.GROUP_ENDED,
            ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN,
            ErrorCode.INVALID_SCHEDULE_TIME);
}

@Test
void rejectsMissingPrincipal() {
    ScheduleController controller = new ScheduleController(scheduleService);

    assertThatThrownBy(() -> controller.create(10L, null, validRequest))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
}
```

Add this HTTP length-validation smoke test; Task 1 already verifies every individual maximum:

```java
@Test
void rejectsOverlongTitle() throws Exception {
    String body =
            """
            {"title":"%s","scheduledAt":"2026-07-25T19:00:00"}
            """
                    .formatted("t".repeat(101));

    mockMvc.perform(
                    post("/api/groups/{groupId}/schedules", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    verifyNoInteractions(scheduleService);
}
```

- [ ] **Step 2: HTTP 테스트의 예상 실패 확인**

```powershell
.\gradlew.bat test --tests com.mycom.myapp.schedule.controller.ScheduleControllerTest
```

Expected: Controller 매핑과 생성자가 없어 테스트가 실패한다.

- [ ] **Step 3: Controller 구현**

```java
@RestController
@RequestMapping("/api/groups/{groupId}/schedules")
public class ScheduleController {
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResponse>> create(
            @PathVariable Long groupId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ScheduleCreateRequest request) {
        if (authenticatedMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ScheduleResponse response = scheduleService.create(groupId, authenticatedMember.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
```

- [ ] **Step 4: Controller 테스트 통과 확인**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: HTTP API 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java
git commit -m "feat: 일정 생성 API 추가"
```

---

### Task 5: 통합 검증과 컨텍스트 마무리

**Files:**
- Create: `src/test/java/com/mycom/myapp/schedule/service/ScheduleCreationIntegrationTest.java`
- Modify: `docs/part3-group/context.md`

**Interfaces:**
- Consumes: 실제 JPA Repository와 `ScheduleService`
- Produces: 트랜잭션 저장 및 문서 계약의 통합 증거

- [ ] **Step 1: 통합 테스트 작성**

Use this integration-test structure:

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScheduleCreationIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 20, 12, 0);
    @Autowired private ScheduleService service;
    @Autowired private StudyGroupRepository groupRepository;
    @Autowired private GroupMemberRepository memberRepository;
    @Autowired private StudyScheduleRepository scheduleRepository;
    @MockitoBean(name = "scheduleClock") private Clock clock;

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
                new ScheduleCreateRequest(
                        "잘못된 일정", NOW, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(group.getId(), 1L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_SCHEDULE_TIME));
        assertThat(scheduleRepository.findAll()).isEmpty();
    }
}
```

- [ ] **Step 2: 통합 테스트 실행**

```powershell
.\gradlew.bat test --tests com.mycom.myapp.schedule.service.ScheduleCreationIntegrationTest
```

Expected: `BUILD SUCCESSFUL` and both persistence and rollback cases pass.

- [ ] **Step 3: 관련 일정 테스트 전체 실행**

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 전체 테스트와 포맷 검증**

```powershell
.\gradlew.bat test --rerun-tasks
.\gradlew.bat spotlessCheck --rerun-tasks
git diff --check
```

Expected: 두 Gradle 명령 모두 `BUILD SUCCESSFUL`, `git diff --check` 출력 없음.

- [ ] **Step 5: 변경 범위 확인**

```powershell
git status --short
git diff --name-only origin/develop...HEAD
```

Expected: `schedule/**`, 대응 `src/test/**`, `docs/part3-group/**`와 합의된 `global/exception/ErrorCode.java`만 포함한다.

- [ ] **Step 6: context 문서 갱신 및 최종 커밋**

`context.md`에 현재 브랜치·HEAD, 일정 생성 완료 내용, 세 오류 코드, 검증 결과와 다음 단계인 일정 조회 API를 기록한다.

```powershell
git add docs/part3-group/context.md src/test/java/com/mycom/myapp/schedule/service/ScheduleCreationIntegrationTest.java
git commit -m "test: 일정 생성 통합 흐름 검증"
```
