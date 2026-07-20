# 일정 수정 API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 활성 `LEADER`와 `MANAGER`가 미래 일정을 `PUT` 전체 교체 방식으로 수정하고, 권한·그룹 상태·일정 상태·시간 오류를 계약대로 구분하는 API를 구현한다.

**Architecture:** `ScheduleUpdateRequest`가 문자열 형식과 길이를 검증하고, `ScheduleService`가 고정된 `Clock` 기준으로 접근 권한과 시간 규칙을 순서대로 검증한다. 검증을 통과한 값만 `StudySchedule.update(...)`에 전달하고 JPA 변경 감지로 저장하며, Controller는 인증 사용자와 HTTP 계약만 연결한다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Web MVC, Jakarta Validation, Spring Data JPA, JUnit 5, AssertJ, Mockito, MockMvc, H2 MySQL compatibility mode, Gradle 9.5.1

## Global Constraints

- 기준 브랜치는 `feature/part3-schedule-update`, 기준 커밋은 `32ef30d`다.
- Part3 구현 경로인 `schedule/**`, 대응 테스트와 `docs/part3-group/**`만 수정한다.
- 공통 영역은 `SCHEDULE_UPDATE_NOT_ALLOWED` 추가와 `GROUP_ENDED` 메시지 일반화를 위한 `global/exception/ErrorCode.java`만 수정한다.
- 일정 삭제, `responseDeadline` 수정, `PATCH`, 스키마 변경, 낙관적 잠금, Part4·활동 코드는 범위에서 제외한다.
- 수정 권한은 `ACTIVE` 상태의 `LEADER`와 `MANAGER`에게만 부여하고 등록자 여부는 확인하지 않는다.
- `ENDED` 그룹과 `scheduledAt <= now`인 기존 일정은 수정할 수 없다.
- 요청의 새 `scheduledAt`은 `now`보다 미래여야 하고 기존 `responseDeadline`보다 같거나 늦어야 한다.
- 검증 순서는 그룹 존재, 그룹원 기록, 탈퇴 상태, 그룹 상태, 역할, 일정·그룹 소속, 기존 일정 상태, 요청 시간, 기존 응답 마감 순서다.
- 테스트를 먼저 작성하고 실패를 확인한 뒤 최소 구현을 추가한다.
- Windows 명령은 `./gradlew.bat` 대신 저장소 관례에 맞는 `.\gradlew.bat`를 사용한다.

---

## 파일 구조

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleUpdateRequest.java` | PUT 수정 본문의 정규화와 형식 검증 |
| `src/test/java/com/mycom/myapp/schedule/dto/request/ScheduleUpdateRequestTest.java` | 수정 요청 정규화·필수값·길이 계약 |
| `src/main/java/com/mycom/myapp/schedule/entity/StudySchedule.java` | 일정 불변식을 보존하는 상태 변경 메서드 |
| `src/test/java/com/mycom/myapp/schedule/entity/StudyScheduleTest.java` | 변경 필드와 보존 필드 검증 |
| `src/main/java/com/mycom/myapp/global/exception/ErrorCode.java` | 시작된 일정 수정 충돌과 범용 종료 그룹 메시지 |
| `src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java` | 수정 유스케이스, 권한·상태·시간 검증 순서 |
| `src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java` | 수정 성공·실패·검증 순서 단위 테스트 |
| `src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java` | PUT 엔드포인트와 공통 응답 연결 |
| `src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java` | HTTP 요청·검증·오류 매핑 계약 |
| `src/test/java/com/mycom/myapp/schedule/service/ScheduleUpdateIntegrationTest.java` | 실제 JPA 변경 감지와 보존 필드 검증 |
| `docs/part3-group/api.md` | 공개 수정 API 계약 |
| `docs/part3-group/context.md` | 최신 Git·로드맵·바로 다음 작업 상태 |
| `docs/part3-group/schedule-update-design.md` | 구현과 대조한 최종 설계 |

---

### Task 1: 수정 요청 DTO와 검증 계약

**Files:**
- Create: `src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleUpdateRequest.java`
- Create: `src/test/java/com/mycom/myapp/schedule/dto/request/ScheduleUpdateRequestTest.java`

**Interfaces:**
- Consumes: JSON의 `title`, `scheduledAt`, `location`, `onlineLink`, `content`, `materials`
- Produces: `ScheduleUpdateRequest(String, LocalDateTime, String, String, String, String)`

- [ ] **Step 1: 정규화와 Bean Validation 실패 테스트 작성**

`ScheduleUpdateRequestTest.java`를 다음 내용으로 만든다.

```java
package com.mycom.myapp.schedule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScheduleUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 28, 19, 0);

    @Test
    void normalizesRequiredAndOptionalStrings() {
        ScheduleUpdateRequest request =
                new ScheduleUpdateRequest(
                        "  수정 일정  ", scheduledAt, "   ", " Discord  ", "  내용  ", "");

        assertThat(request.title()).isEqualTo("수정 일정");
        assertThat(request.location()).isNull();
        assertThat(request.onlineLink()).isEqualTo("Discord");
        assertThat(request.content()).isEqualTo("내용");
        assertThat(request.materials()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankTitleAndMissingScheduledAt() {
        ScheduleUpdateRequest request =
                new ScheduleUpdateRequest("   ", null, null, null, null, null);

        assertThat(propertiesOf(validator.validate(request)))
                .containsExactlyInAnyOrder("title", "scheduledAt");
    }

    @Test
    void rejectsValuesBeyondDocumentedLengths() {
        ScheduleUpdateRequest request =
                new ScheduleUpdateRequest(
                        "t".repeat(101),
                        scheduledAt,
                        "l".repeat(256),
                        "o".repeat(501),
                        "c".repeat(5001),
                        "m".repeat(5001));

        assertThat(propertiesOf(validator.validate(request)))
                .containsExactlyInAnyOrder(
                        "title", "location", "onlineLink", "content", "materials");
    }

    private Set<String> propertiesOf(Set<ConstraintViolation<ScheduleUpdateRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
```

- [ ] **Step 2: DTO 테스트가 컴파일 실패하는지 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.dto.request.ScheduleUpdateRequestTest"
```

Expected: `cannot find symbol: class ScheduleUpdateRequest`로 실패한다.

- [ ] **Step 3: 최소 DTO 구현 추가**

`ScheduleUpdateRequest.java`를 다음 내용으로 만든다.

```java
package com.mycom.myapp.schedule.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ScheduleUpdateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull LocalDateTime scheduledAt,
        @Size(max = 255) String location,
        @Size(max = 500) String onlineLink,
        @Size(max = 5000) String content,
        @Size(max = 5000) String materials) {

    public ScheduleUpdateRequest {
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

- [ ] **Step 4: DTO 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.dto.request.ScheduleUpdateRequestTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 5: DTO 변경 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleUpdateRequest.java src/test/java/com/mycom/myapp/schedule/dto/request/ScheduleUpdateRequestTest.java
git commit -m "feat: 일정 수정 요청 검증 추가"
```

---

### Task 2: 일정 Entity 변경 동작

**Files:**
- Modify: `src/main/java/com/mycom/myapp/schedule/entity/StudySchedule.java:121-130`
- Modify: `src/test/java/com/mycom/myapp/schedule/entity/StudyScheduleTest.java:51`

**Interfaces:**
- Consumes: 정규화·시간 검증을 통과한 제목, 일정 시각, 선택 문자열 네 개와 수정 시각
- Produces: `void StudySchedule.update(String, LocalDateTime, String, String, String, String, LocalDateTime)`

- [ ] **Step 1: 변경 필드와 보존 필드 Entity 테스트 작성**

`StudyScheduleTest`에 다음 테스트를 추가한다.

```java
@Test
void updatesEditableFieldsAndPreservesOwnedReferencesAndDeadline() {
    LocalDateTime responseDeadline = scheduledAt.minusHours(1);
    LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 10, 0);
    LocalDateTime previousUpdatedAt = LocalDateTime.of(2026, 7, 20, 10, 0);
    LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 20, 12, 0);
    StudySchedule schedule =
            StudySchedule.create(
                    group,
                    1L,
                    "기존 일정",
                    scheduledAt,
                    "기존 장소",
                    "기존 링크",
                    "기존 내용",
                    "기존 준비물",
                    responseDeadline);
    ReflectionTestUtils.setField(schedule, "createdAt", createdAt);
    ReflectionTestUtils.setField(schedule, "updatedAt", previousUpdatedAt);

    schedule.update(
            "수정 일정",
            scheduledAt.plusDays(1),
            null,
            "Discord",
            "수정 내용",
            null,
            modifiedAt);

    assertThat(schedule.getTitle()).isEqualTo("수정 일정");
    assertThat(schedule.getScheduledAt()).isEqualTo(scheduledAt.plusDays(1));
    assertThat(schedule.getLocation()).isNull();
    assertThat(schedule.getOnlineLink()).isEqualTo("Discord");
    assertThat(schedule.getContent()).isEqualTo("수정 내용");
    assertThat(schedule.getMaterials()).isNull();
    assertThat(schedule.getStudyGroup()).isSameAs(group);
    assertThat(schedule.getCreatorId()).isEqualTo(1L);
    assertThat(schedule.getResponseDeadline()).isEqualTo(responseDeadline);
    assertThat(schedule.getCreatedAt()).isEqualTo(createdAt);
    assertThat(schedule.getUpdatedAt()).isEqualTo(modifiedAt);
}

@Test
void rejectsUpdateThatBreaksDeadlineInvariant() {
    StudySchedule schedule =
            StudySchedule.create(
                    group,
                    1L,
                    "기존 일정",
                    scheduledAt,
                    null,
                    null,
                    null,
                    null,
                    scheduledAt.minusHours(1));

    assertThatIllegalArgumentException()
            .isThrownBy(
                    () ->
                            schedule.update(
                                    "수정 일정",
                                    scheduledAt.minusHours(2),
                                    null,
                                    null,
                                    null,
                                    null,
                                    scheduledAt.minusDays(1)));
}
```

`org.springframework.test.util.ReflectionTestUtils` import를 추가한다.

- [ ] **Step 2: Entity 테스트 실패 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.entity.StudyScheduleTest"
```

Expected: `StudySchedule.update(...)`가 없어 컴파일 실패한다.

- [ ] **Step 3: Entity 변경 메서드 구현**

`initializeTimestamps()` 아래에 다음 메서드를 추가한다.

```java
public void update(
        String title,
        LocalDateTime scheduledAt,
        String location,
        String onlineLink,
        String content,
        String materials,
        LocalDateTime modifiedAt) {
    if (title == null || title.isBlank() || scheduledAt == null || modifiedAt == null) {
        throw new IllegalArgumentException("일정 수정 필수값이 누락되었습니다.");
    }
    if (responseDeadline != null && responseDeadline.isAfter(scheduledAt)) {
        throw new IllegalArgumentException("응답 마감 시간은 일정 시간보다 늦을 수 없습니다.");
    }
    this.title = title;
    this.scheduledAt = scheduledAt;
    this.location = location;
    this.onlineLink = onlineLink;
    this.content = content;
    this.materials = materials;
    this.updatedAt = modifiedAt;
}
```

- [ ] **Step 4: Entity 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.entity.StudyScheduleTest"
```

Expected: `BUILD SUCCESSFUL`, 기존 생성 테스트와 신규 수정 테스트가 모두 통과한다.

- [ ] **Step 5: Entity 변경 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/entity/StudySchedule.java src/test/java/com/mycom/myapp/schedule/entity/StudyScheduleTest.java
git commit -m "feat: 일정 엔티티 수정 동작 추가"
```

---

### Task 3: 권한·상태·시간 기반 수정 Service

**Files:**
- Modify: `src/main/java/com/mycom/myapp/global/exception/ErrorCode.java:18-21`
- Modify: `src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java:5-125`
- Modify: `src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java:14-302`

**Interfaces:**
- Consumes: `ScheduleUpdateRequest`, 기존 `findByIdAndStudyGroupId(Long, Long)`, 고정 `Clock`
- Produces: `ScheduleResponse ScheduleService.update(Long groupId, Long memberId, Long scheduleId, ScheduleUpdateRequest request)`

- [ ] **Step 1: 수정 성공과 보존 필드 Service 테스트 작성**

`ScheduleServiceTest`에 `ScheduleUpdateRequest` import와 다음 테스트·도우미를 추가한다.

```java
@ParameterizedTest
@EnumSource(
        value = GroupRole.class,
        names = {"LEADER", "MANAGER"})
void updatesFutureScheduleForManagementRole(GroupRole role) {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, role));
    LocalDateTime responseDeadline = NOW.minusHours(1);
    StudySchedule schedule =
            schedule(group, 100L, NOW.plusHours(2), responseDeadline);
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule));

    ScheduleResponse response =
            service.update(10L, 1L, 100L, updateRequest(NOW.plusHours(3)));

    assertThat(response.title()).isEqualTo("수정 일정");
    assertThat(response.scheduledAt()).isEqualTo(NOW.plusHours(3));
    assertThat(response.location()).isEqualTo("수정 장소");
    assertThat(response.responseDeadline()).isEqualTo(responseDeadline);
    assertThat(response.creatorId()).isEqualTo(1L);
    assertThat(response.updatedAt()).isEqualTo(NOW);
    verify(scheduleRepository).findByIdAndStudyGroupId(100L, 10L);
    verify(scheduleRepository, never()).save(any());
}

private StudySchedule schedule(
        StudyGroup group,
        Long scheduleId,
        LocalDateTime scheduledAt,
        LocalDateTime responseDeadline) {
    StudySchedule schedule =
            StudySchedule.create(
                    group,
                    1L,
                    "조회 일정",
                    scheduledAt,
                    null,
                    null,
                    null,
                    null,
                    responseDeadline);
    ReflectionTestUtils.setField(schedule, "id", scheduleId);
    ReflectionTestUtils.setField(schedule, "createdAt", NOW.minusDays(1));
    ReflectionTestUtils.setField(schedule, "updatedAt", NOW.minusDays(1));
    return schedule;
}

private ScheduleUpdateRequest updateRequest(LocalDateTime scheduledAt) {
    return new ScheduleUpdateRequest(
            "수정 일정", scheduledAt, "수정 장소", null, "수정 내용", null);
}
```

기존 세 인자 `schedule(...)` 도우미는 새 네 인자 도우미를 호출하도록 바꾼다.

```java
private StudySchedule schedule(StudyGroup group, Long scheduleId, LocalDateTime scheduledAt) {
    return schedule(group, scheduleId, scheduledAt, null);
}
```

- [ ] **Step 2: 수정 Service가 없어 실패하는지 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleServiceTest.updatesFutureScheduleForManagementRole"
```

Expected: `ScheduleService.update(...)`가 없어 컴파일 실패한다.

- [ ] **Step 3: 오류 코드와 Service 최소 구현 추가**

`ErrorCode`의 일정 오류 구간을 다음과 같이 바꾼다.

```java
GROUP_ENDED(HttpStatus.CONFLICT, "종료된 그룹에서는 일정을 관리할 수 없습니다."),
SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
SCHEDULE_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "일정을 관리할 권한이 없습니다."),
SCHEDULE_UPDATE_NOT_ALLOWED(HttpStatus.CONFLICT, "이미 시작된 일정은 수정할 수 없습니다."),
INVALID_SCHEDULE_TIME(HttpStatus.BAD_REQUEST, "일정 또는 응답 마감 시간이 올바르지 않습니다."),
```

`ScheduleService`에 `ScheduleUpdateRequest` import를 추가하고 생성 메서드의 그룹 상태·역할 검증을
`validateScheduleManagement(...)`로 이동한 뒤 다음 수정 메서드를 추가한다.

```java
@Transactional
public ScheduleResponse update(
        Long groupId, Long memberId, Long scheduleId, ScheduleUpdateRequest request) {
    StudyGroup group = getGroup(groupId);
    GroupMember member = getActiveMember(groupId, memberId);
    validateScheduleManagement(group, member);

    StudySchedule schedule =
            scheduleRepository
                    .findByIdAndStudyGroupId(scheduleId, groupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    LocalDateTime now = LocalDateTime.now(clock);
    if (!schedule.getScheduledAt().isAfter(now)) {
        throw new BusinessException(ErrorCode.SCHEDULE_UPDATE_NOT_ALLOWED);
    }
    if (!request.scheduledAt().isAfter(now)
            || (schedule.getResponseDeadline() != null
                    && schedule.getResponseDeadline().isAfter(request.scheduledAt()))) {
        throw new BusinessException(ErrorCode.INVALID_SCHEDULE_TIME);
    }

    schedule.update(
            request.title(),
            request.scheduledAt(),
            request.location(),
            request.onlineLink(),
            request.content(),
            request.materials(),
            now);
    return ScheduleResponse.from(schedule);
}

private void validateScheduleManagement(StudyGroup group, GroupMember member) {
    if (group.getStatus() == GroupStatus.ENDED) {
        throw new BusinessException(ErrorCode.GROUP_ENDED);
    }
    if (member.getRole() != GroupRole.LEADER && member.getRole() != GroupRole.MANAGER) {
        throw new BusinessException(ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN);
    }
}
```

`create(...)`의 기존 두 `if` 블록은 다음 호출로 교체한다.

```java
validateScheduleManagement(group, member);
```

- [ ] **Step 4: 수정 성공 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleServiceTest.updatesFutureScheduleForManagementRole"
```

Expected: `BUILD SUCCESSFUL`, `LEADER`와 `MANAGER` 두 invocation이 통과한다.

- [ ] **Step 5: 검증 순서와 오류 테스트 작성**

`ScheduleServiceTest`에 다음 테스트를 추가한다.

```java
@Test
void rejectsUpdateForEndedGroupBeforeRoleAndScheduleLookup() {
    StudyGroup group = activeGroup();
    group.end();
    allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));

    assertUpdateError(ErrorCode.GROUP_ENDED, updateRequest(NOW.plusHours(3)));

    verifyNoInteractions(scheduleRepository);
}

@Test
void rejectsUpdateForMemberBeforeScheduleLookup() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));

    assertUpdateError(
            ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN, updateRequest(NOW.plusHours(3)));

    verifyNoInteractions(scheduleRepository);
}

@Test
void reportsScheduleNotFoundBeforeTimeValidation() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L)).thenReturn(Optional.empty());

    assertUpdateError(ErrorCode.SCHEDULE_NOT_FOUND, updateRequest(NOW));
}

@ParameterizedTest
@MethodSource("startedScheduleTimes")
void rejectsStartedScheduleWithStateConflict(LocalDateTime existingScheduledAt) {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule(group, 100L, existingScheduledAt, null)));

    assertUpdateError(
            ErrorCode.SCHEDULE_UPDATE_NOT_ALLOWED, updateRequest(NOW.plusHours(3)));
}

@ParameterizedTest
@MethodSource("invalidNewScheduleTimes")
void rejectsNewScheduleTimeAtOrBeforeNow(LocalDateTime requestedScheduledAt) {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule(group, 100L, NOW.plusHours(2), null)));

    assertUpdateError(ErrorCode.INVALID_SCHEDULE_TIME, updateRequest(requestedScheduledAt));
}

@Test
void rejectsNewScheduleTimeBeforePreservedDeadline() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(
                    Optional.of(
                            schedule(group, 100L, NOW.plusHours(4), NOW.plusHours(3))));

    assertUpdateError(
            ErrorCode.INVALID_SCHEDULE_TIME, updateRequest(NOW.plusHours(2)));
}

@Test
void allowsNewScheduleTimeEqualToPreservedDeadline() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.LEADER));
    LocalDateTime deadline = NOW.plusHours(3);
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule(group, 100L, NOW.plusHours(4), deadline)));

    ScheduleResponse response = service.update(10L, 1L, 100L, updateRequest(deadline));

    assertThat(response.scheduledAt()).isEqualTo(deadline);
    assertThat(response.responseDeadline()).isEqualTo(deadline);
}

private static Stream<LocalDateTime> startedScheduleTimes() {
    return Stream.of(NOW, NOW.minusSeconds(1));
}

private static Stream<LocalDateTime> invalidNewScheduleTimes() {
    return Stream.of(NOW, NOW.minusSeconds(1));
}

private void assertUpdateError(ErrorCode errorCode, ScheduleUpdateRequest request) {
    assertThatThrownBy(() -> service.update(10L, 1L, 100L, request))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
}
```

기존 그룹 없음·그룹원 없음·탈퇴 상태 테스트 패턴을 수정 유스케이스에도 적용해 다음 세 테스트를
추가한다. 각 테스트는 `assertUpdateError(...)` 뒤에 아직 접근하면 안 되는 mock을 검증한다.

```java
@Test
void rejectsUpdateForMissingGroupBeforeMembershipLookup() {
    when(groupRepository.findById(10L)).thenReturn(Optional.empty());

    assertUpdateError(ErrorCode.GROUP_NOT_FOUND, updateRequest(NOW.plusHours(3)));

    verifyNoInteractions(memberRepository, scheduleRepository);
}

@Test
void rejectsUpdateForMissingMembershipBeforeScheduleLookup() {
    StudyGroup group = activeGroup();
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(memberRepository.findByStudyGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

    assertUpdateError(ErrorCode.GROUP_ACCESS_DENIED, updateRequest(NOW.plusHours(3)));

    verifyNoInteractions(scheduleRepository);
}

@Test
void rejectsUpdateForWithdrawnMemberBeforeScheduleLookup() {
    StudyGroup group = activeGroup();
    GroupMember member = GroupMember.join(group, 1L, GroupRole.LEADER);
    member.withdraw();
    allow(group, member);

    assertUpdateError(ErrorCode.WITHDRAWN_GROUP_MEMBER, updateRequest(NOW.plusHours(3)));

    verifyNoInteractions(scheduleRepository);
}
```

- [ ] **Step 6: 전체 ScheduleService 단위 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleServiceTest"
```

Expected: `BUILD SUCCESSFUL`; 생성·조회 회귀 테스트와 수정 성공·실패 테스트가 모두 통과한다.

- [ ] **Step 7: Service 변경 커밋**

```powershell
git add src/main/java/com/mycom/myapp/global/exception/ErrorCode.java src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java
git commit -m "feat: 권한 기반 일정 수정 서비스 추가"
```

---

### Task 4: PUT Controller와 HTTP 계약

**Files:**
- Modify: `src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java:7-72`
- Modify: `src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java:1-288`

**Interfaces:**
- Consumes: `PUT /api/groups/{groupId}/schedules/{scheduleId}`, 인증 Principal, `ScheduleUpdateRequest`
- Produces: `ApiResponse<ScheduleResponse>`와 `200 OK`

- [ ] **Step 1: PUT 성공·검증·오류 Controller 테스트 작성**

테스트에 다음 static import와 DTO import를 추가한다.

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.mycom.myapp.schedule.dto.request.ScheduleUpdateRequest;
```

다음 요청 JSON과 테스트를 추가한다.

```java
private final String validUpdateRequestJson =
        """
        {"title":"수정 일정","scheduledAt":"2026-07-28T19:00:00","location":"수정 장소","onlineLink":null,"content":"수정 내용","materials":null}
        """;

@Test
void updatesScheduleAndReturnsFullResponse() throws Exception {
    when(scheduleService.update(eq(10L), eq(1L), eq(100L), any(ScheduleUpdateRequest.class)))
            .thenReturn(response());

    mockMvc.perform(
                    put("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 100L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validUpdateRequestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.scheduleId").value(100))
            .andExpect(jsonPath("$.data.groupId").value(10));
    verify(scheduleService)
            .update(eq(10L), eq(1L), eq(100L), any(ScheduleUpdateRequest.class));
}

@Test
void rejectsInvalidUpdateBodyBeforeServiceCall() throws Exception {
    mockMvc.perform(
                    put("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 100L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"   \",\"scheduledAt\":null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    verifyNoInteractions(scheduleService);
}

@ParameterizedTest
@MethodSource("updateBusinessErrors")
void mapsUpdateBusinessErrors(ErrorCode errorCode) throws Exception {
    when(scheduleService.update(eq(10L), eq(1L), eq(100L), any(ScheduleUpdateRequest.class)))
            .thenThrow(new BusinessException(errorCode));

    mockMvc.perform(
                    put("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 100L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validUpdateRequestJson))
            .andExpect(status().is(errorCode.getStatus().value()))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
}

@Test
void rejectsMissingPrincipalForScheduleUpdate() {
    ScheduleController controller = new ScheduleController(scheduleService);
    ScheduleUpdateRequest request =
            new ScheduleUpdateRequest(
                    "수정 일정",
                    LocalDateTime.of(2026, 7, 28, 19, 0),
                    null,
                    null,
                    null,
                    null);

    assertThatThrownBy(() -> controller.update(10L, 100L, null, request))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
}

private static Stream<ErrorCode> updateBusinessErrors() {
    return Stream.of(
            ErrorCode.GROUP_NOT_FOUND,
            ErrorCode.GROUP_ACCESS_DENIED,
            ErrorCode.WITHDRAWN_GROUP_MEMBER,
            ErrorCode.GROUP_ENDED,
            ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN,
            ErrorCode.SCHEDULE_NOT_FOUND,
            ErrorCode.SCHEDULE_UPDATE_NOT_ALLOWED,
            ErrorCode.INVALID_SCHEDULE_TIME);
}
```

- [ ] **Step 2: PUT 매핑 부재로 테스트 실패 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.controller.ScheduleControllerTest.updatesScheduleAndReturnsFullResponse"
```

Expected: `405 Method Not Allowed` 또는 `ScheduleController.update(...)` 컴파일 실패다.

- [ ] **Step 3: Controller PUT 구현**

다음 import를 추가한다.

```java
import com.mycom.myapp.schedule.dto.request.ScheduleUpdateRequest;
import org.springframework.web.bind.annotation.PutMapping;
```

상세 조회 메서드 앞에 다음 메서드를 추가한다.

```java
@PutMapping("/{scheduleId}")
public ApiResponse<ScheduleResponse> update(
        @PathVariable Long groupId,
        @PathVariable Long scheduleId,
        @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
        @Valid @RequestBody ScheduleUpdateRequest request) {
    Long memberId = requireAuthenticatedMemberId(authenticatedMember);
    return ApiResponse.success(scheduleService.update(groupId, memberId, scheduleId, request));
}
```

- [ ] **Step 4: Controller 전체 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.controller.ScheduleControllerTest"
```

Expected: `BUILD SUCCESSFUL`; 생성·조회 회귀 테스트와 PUT 성공·검증·오류 테스트가 모두 통과한다.

- [ ] **Step 5: Controller 변경 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java
git commit -m "feat: 일정 수정 API 추가"
```

---

### Task 5: 실제 JPA 수정 통합 흐름

**Files:**
- Create: `src/test/java/com/mycom/myapp/schedule/service/ScheduleUpdateIntegrationTest.java`

**Interfaces:**
- Consumes: 실제 `ScheduleService`, 세 Part3 Repository, `scheduleClock`
- Produces: 변경 감지·필드 보존·실패 시 무변경을 검증하는 Spring 통합 테스트

- [ ] **Step 1: 수정 통합 테스트 작성**

다음 파일을 만든다.

```java
package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.schedule.dto.request.ScheduleUpdateRequest;
import com.mycom.myapp.schedule.dto.response.ScheduleResponse;
import com.mycom.myapp.schedule.entity.StudySchedule;
import com.mycom.myapp.schedule.repository.StudyScheduleRepository;
import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import jakarta.persistence.EntityManager;
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
            "spring.datasource.url=jdbc:h2:mem:schedule_update;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@Transactional
class ScheduleUpdateIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 20, 12, 0);

    @Autowired private ScheduleService service;
    @Autowired private StudyGroupRepository groupRepository;
    @Autowired private GroupMemberRepository memberRepository;
    @Autowired private StudyScheduleRepository scheduleRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean(name = "scheduleClock")
    private Clock clock;

    @BeforeEach
    void fixClock() {
        when(clock.instant()).thenReturn(NOW.atZone(ZONE).toInstant());
        when(clock.getZone()).thenReturn(ZONE);
    }

    @Test
    void updatesScheduleAndPreservesCreatorDeadlineAndCreatedAt() {
        StudyGroup group = saveGroupWithLeader(25L, 1L);
        LocalDateTime responseDeadline = NOW.minusHours(1);
        StudySchedule schedule =
                scheduleRepository.saveAndFlush(
                        StudySchedule.create(
                                group,
                                9L,
                                "기존 일정",
                                NOW.plusDays(2),
                                "기존 장소",
                                null,
                                "기존 내용",
                                "기존 준비물",
                                responseDeadline));
        LocalDateTime createdAt = schedule.getCreatedAt();
        Long scheduleId = schedule.getId();

        ScheduleResponse response =
                service.update(
                        group.getId(),
                        1L,
                        scheduleId,
                        new ScheduleUpdateRequest(
                                "수정 일정",
                                NOW.plusDays(3),
                                null,
                                "Discord",
                                "수정 내용",
                                null));
        entityManager.flush();
        entityManager.clear();
        StudySchedule persisted = scheduleRepository.findById(scheduleId).orElseThrow();

        assertThat(response.title()).isEqualTo("수정 일정");
        assertThat(persisted.getScheduledAt()).isEqualTo(NOW.plusDays(3));
        assertThat(persisted.getLocation()).isNull();
        assertThat(persisted.getOnlineLink()).isEqualTo("Discord");
        assertThat(persisted.getCreatorId()).isEqualTo(9L);
        assertThat(persisted.getResponseDeadline()).isEqualTo(responseDeadline);
        assertThat(persisted.getCreatedAt()).isEqualTo(createdAt);
        assertThat(persisted.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void doesNotChangeScheduleWhenNewTimeConflictsWithDeadline() {
        StudyGroup group = saveGroupWithLeader(25L, 1L);
        LocalDateTime originalScheduledAt = NOW.plusDays(3);
        StudySchedule schedule =
                scheduleRepository.saveAndFlush(
                        StudySchedule.create(
                                group,
                                1L,
                                "기존 일정",
                                originalScheduledAt,
                                null,
                                null,
                                null,
                                null,
                                NOW.plusDays(2)));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        group.getId(),
                                        1L,
                                        schedule.getId(),
                                        new ScheduleUpdateRequest(
                                                "수정 일정",
                                                NOW.plusDays(1),
                                                null,
                                                null,
                                                null,
                                                null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_SCHEDULE_TIME));
        assertThat(schedule.getTitle()).isEqualTo("기존 일정");
        assertThat(schedule.getScheduledAt()).isEqualTo(originalScheduledAt);
    }

    @Test
    void reportsNotFoundForScheduleOwnedByAnotherGroup() {
        StudyGroup allowedGroup = saveGroupWithLeader(25L, 1L);
        StudyGroup otherGroup = groupRepository.saveAndFlush(StudyGroup.create(26L, "다른 그룹"));
        StudySchedule otherSchedule =
                scheduleRepository.saveAndFlush(
                        StudySchedule.create(
                                otherGroup,
                                2L,
                                "다른 그룹 일정",
                                NOW.plusDays(2),
                                null,
                                null,
                                null,
                                null,
                                null));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        allowedGroup.getId(),
                                        1L,
                                        otherSchedule.getId(),
                                        new ScheduleUpdateRequest(
                                                "수정 일정",
                                                NOW.plusDays(3),
                                                null,
                                                null,
                                                null,
                                                null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND));
        assertThat(otherSchedule.getTitle()).isEqualTo("다른 그룹 일정");
    }

    private StudyGroup saveGroupWithLeader(Long postId, Long leaderId) {
        StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(postId, "알고리즘 스터디"));
        memberRepository.saveAndFlush(GroupMember.join(group, leaderId, GroupRole.LEADER));
        return group;
    }
}
```

- [ ] **Step 2: 통합 테스트 실행**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleUpdateIntegrationTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed. 성공 테스트는 flush·clear 후 수정값과 보존값을 확인하고 실패 테스트는 기존 Entity가 변하지 않았음을 확인한다.

- [ ] **Step 3: 일정 패키지 회귀 테스트 실행**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.*"
```

Expected: `BUILD SUCCESSFUL`; 생성·조회·수정 관련 테스트가 모두 통과한다.

- [ ] **Step 4: 통합 테스트 커밋**

```powershell
git add src/test/java/com/mycom/myapp/schedule/service/ScheduleUpdateIntegrationTest.java
git commit -m "test: 일정 수정 통합 흐름 검증"
```

---

### Task 6: API 계약과 Part3 컨텍스트 동기화

**Files:**
- Modify: `docs/part3-group/api.md`
- Modify: `docs/part3-group/context.md`

**Interfaces:**
- Consumes: 실제 구현된 PUT 요청·응답·오류와 Git 커밋 상태
- Produces: 코드와 일치하는 Part3 공개 계약과 다음 세션 시작점

- [ ] **Step 1: API 엔드포인트 표와 그룹 종료 메시지 갱신**

`api.md` 엔드포인트 표에 다음 행을 추가한다.

```markdown
| 일정 수정 | `PUT` | `/api/groups/{groupId}/schedules/{scheduleId}` | 필수 |
```

일정 생성 오류 표의 `GROUP_ENDED` 메시지를 다음으로 바꾼다.

```markdown
| 그룹이 종료됨 | `409 Conflict` | `GROUP_ENDED` | `종료된 그룹에서는 일정을 관리할 수 없습니다.` |
```

- [ ] **Step 2: 일정 수정 API 계약 추가**

일정 조회 절 뒤에 다음 계약을 추가한다.

```markdown
## 일정 수정

### 요청

`PUT /api/groups/{groupId}/schedules/{scheduleId}`는 수정 가능한 일정 필드 전체를 교체한다.
요청 필드는 `title`, `scheduledAt`, `location`, `onlineLink`, `content`, `materials`다.
`title`과 `scheduledAt`은 필수이고 문자열 정규화·길이는 일정 생성과 같다.
`responseDeadline`은 Part4와 변경 정책을 합의할 때까지 수정 대상에서 제외한다.

### 권한과 시간 규칙

- 활성 `LEADER`와 `MANAGER`만 수정할 수 있다.
- `MEMBER`, 비회원과 탈퇴 그룹원은 수정할 수 없다.
- `ENDED` 그룹과 이미 시작된 일정은 수정할 수 없다.
- 새 `scheduledAt`은 현재보다 미래이고 기존 `responseDeadline`보다 같거나 늦어야 한다.
- 등록자, 응답 마감과 생성 시각은 유지하고 수정 시각만 갱신한다.

### 성공 응답

- HTTP 상태: `200 OK`
- 응답 타입: `ApiResponse<ScheduleResponse>`

### 오류 응답

| 상황 | HTTP 상태 | 오류 코드 | 메시지 |
| --- | --- | --- | --- |
| 그룹이 존재하지 않음 | `404 Not Found` | `GROUP_NOT_FOUND` | `그룹을 찾을 수 없습니다.` |
| 그룹원 기록이 없음 | `403 Forbidden` | `GROUP_ACCESS_DENIED` | `그룹에 접근할 권한이 없습니다.` |
| 탈퇴 그룹원 | `403 Forbidden` | `WITHDRAWN_GROUP_MEMBER` | `탈퇴한 그룹원은 그룹에 접근할 수 없습니다.` |
| 종료 그룹 | `409 Conflict` | `GROUP_ENDED` | `종료된 그룹에서는 일정을 관리할 수 없습니다.` |
| 관리 역할이 아님 | `403 Forbidden` | `SCHEDULE_MANAGEMENT_FORBIDDEN` | `일정을 관리할 권한이 없습니다.` |
| 일정이 없거나 다른 그룹 소속 | `404 Not Found` | `SCHEDULE_NOT_FOUND` | `일정을 찾을 수 없습니다.` |
| 기존 일정이 이미 시작됨 | `409 Conflict` | `SCHEDULE_UPDATE_NOT_ALLOWED` | `이미 시작된 일정은 수정할 수 없습니다.` |
| 새 일정 시간 규칙 위반 | `400 Bad Request` | `INVALID_SCHEDULE_TIME` | `일정 또는 응답 마감 시간이 올바르지 않습니다.` |
```

- [ ] **Step 3: 컨텍스트를 실제 Git 상태와 로드맵에 맞춰 갱신**

`context.md`에서 다음 내용을 반영한다.

```markdown
- 일정 조회 PR #12가 `develop`에 병합되었다.
- 현재 브랜치는 `feature/part3-schedule-update`다.
- 6단계 일정 조회 API는 완료다.
- 7단계를 일정 수정과 삭제로 분리했다.
- 일정 수정은 PUT 전체 교체, 활성 LEADER·MANAGER 권한으로 구현한다.
- 일정 삭제와 responseDeadline 수정은 파트 간 정책 합의까지 보류한다.
```

`바로 다음 작업`은 Task 7 전체 검증을 완료한 뒤 현재 브랜치를 push하고 `develop` 대상 PR을
준비하는 것으로 기록한다. HEAD 값은 문서 커밋 직전의 실제 `git log -1 --oneline` 결과를
사용한다.

- [ ] **Step 4: 문서 계약 검색과 diff 검증**

Run:

```powershell
rg -n "일정 수정|SCHEDULE_UPDATE_NOT_ALLOWED|GROUP_ENDED|responseDeadline|일정 삭제" docs/part3-group/api.md docs/part3-group/context.md docs/part3-group/schedule-update-design.md
git diff --check
```

Expected: PUT 계약·보류 범위·오류 메시지가 세 문서에서 일치하고 `git diff --check` 출력이 없다.

- [ ] **Step 5: 문서 변경 커밋**

```powershell
git add docs/part3-group/api.md docs/part3-group/context.md
git commit -m "docs: 일정 수정 API 계약과 작업 상태 반영"
```

---

### Task 7: 전체 검증과 브랜치 인계 준비

**Files:**
- Verify only: 전체 작업 트리

**Interfaces:**
- Consumes: Tasks 1-6의 모든 구현·테스트·문서
- Produces: CI에 제출 가능한 깨끗한 브랜치와 검증 기록

- [ ] **Step 1: Spotless 자동 포맷 적용**

Run:

```powershell
.\gradlew.bat spotlessApply
```

Expected: `BUILD SUCCESSFUL`. 변경 파일이 생기면 해당 논리 커밋에 포함될 파일인지 확인한다.

- [ ] **Step 2: 일정 패키지 집중 테스트**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.*" --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, 실패 0건.

- [ ] **Step 3: 전체 테스트 실행**

Run:

```powershell
.\gradlew.bat test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, 실패 0건.

- [ ] **Step 4: Spotless 검사 실행**

Run:

```powershell
.\gradlew.bat spotlessCheck --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Git 변경 범위 검증**

Run:

```powershell
git diff --check
git status --short
git diff --stat origin/develop...HEAD
git log --oneline origin/develop..HEAD
```

Expected:

- `git diff --check` 출력 없음
- 미커밋 파일 없음
- Part3 일정·테스트·문서와 합의된 `ErrorCode.java`만 변경
- 설계, DTO, Entity, Service, Controller, 통합 테스트, 문서의 논리적 커밋이 순서대로 표시

- [ ] **Step 6: 포맷 변경이 남았을 때만 최소 커밋**

`git status --short`에 Task 1-6 파일의 Spotless 변경만 남아 있을 때 실행한다.

```powershell
git add src/main/java/com/mycom/myapp/schedule src/test/java/com/mycom/myapp/schedule src/main/java/com/mycom/myapp/global/exception/ErrorCode.java
git commit -m "chore: 일정 수정 코드 포맷 정리"
```

포맷 변경이 없으면 이 단계는 실행하지 않는다.

## 최종 완료 기준

- `PUT /api/groups/{groupId}/schedules/{scheduleId}`가 전체 교체 계약으로 동작한다.
- 활성 `LEADER`·`MANAGER`만 미래 일정을 수정한다.
- `MEMBER`, 비회원, 탈퇴 그룹원, 종료 그룹, 다른 그룹 일정과 시작된 일정이 계약된 오류로 거부된다.
- 기존 `responseDeadline`, 등록자와 생성 시각은 보존되고 `updatedAt`만 고정 `Clock`의 현재 시각으로 갱신된다.
- 일정 삭제와 응답 마감 수정은 코드·문서 모두 후속 작업으로 남는다.
- 일정 집중 테스트, 전체 테스트, `spotlessCheck`, `git diff --check`가 모두 통과한다.
