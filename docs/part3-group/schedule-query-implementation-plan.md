# Schedule Query API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 활성 그룹원이 예정·지난 일정 목록을 페이지 단위로 조회하고 그룹에 속한 일정의 상세 내용을 조회할 수 있게 한다.

**Architecture:** 기존 `ScheduleController`와 `ScheduleService`에 읽기 전용 유스케이스를 추가한다. 목록은 `ScheduleScope`와 명시적인 페이지 응답 DTO를 사용하고, Repository가 그룹·기준 시각·고정 정렬 조건으로 페이지를 조회한다. 상세 조회는 그룹 접근 권한을 먼저 검증한 뒤 `(scheduleId, groupId)` 조건으로 일정을 찾는다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Web MVC, Spring Data JPA, Jakarta Validation, JUnit 5, AssertJ, Mockito, MockMvc, H2(MySQL mode), Gradle 9.5.1

## Global Constraints

- Part3 작업 경로는 `study`, `schedule`, 대응 테스트와 `docs/part3-group/**`로 제한한다.
- 공통 영역 변경은 `global/exception/ErrorCode.java`에 `SCHEDULE_NOT_FOUND`를 추가하는 것만 허용한다.
- 목록 기본 범위는 `upcoming`, 기본 페이지는 `0`, 기본 크기는 `20`, 최대 크기는 `100`이다.
- 예정 일정은 `scheduledAt >= now`이며 `(scheduledAt ASC, id ASC)`로 정렬한다.
- 지난 일정은 `scheduledAt < now`이며 `(scheduledAt DESC, id DESC)`로 정렬한다.
- 목록과 상세는 역할과 관계없이 활성 그룹원에게 허용하고 종료 그룹도 조회를 허용한다.
- 검증 순서는 그룹 존재, 그룹원 기록, 탈퇴 상태, 상세 조회 시 일정 존재·그룹 소속 순이다.
- Entity와 Spring Data `Page`를 API 응답에 직접 노출하지 않는다.
- 각 구현 작업은 실패 테스트 실행, 최소 구현, 통과 확인, 커밋 순서로 진행한다.

---

## File Map

### Create

- `src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleScope.java`: 예정·지난 조회 범위와 문자열 변환
- `src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleQueryRequest.java`: scope/page/size 파싱과 검증
- `src/main/java/com/mycom/myapp/schedule/dto/response/ScheduleSummaryResponse.java`: 목록 항목 응답
- `src/main/java/com/mycom/myapp/schedule/dto/response/SchedulePageResponse.java`: 명시적인 페이지 응답
- `src/test/java/com/mycom/myapp/schedule/dto/request/ScheduleQueryRequestTest.java`: 조회 파라미터 계약 테스트
- `src/test/java/com/mycom/myapp/schedule/dto/response/SchedulePageResponseTest.java`: Entity 페이지 변환 테스트
- `src/test/java/com/mycom/myapp/schedule/service/ScheduleQueryIntegrationTest.java`: 실제 Repository와 Service 조회 흐름 테스트

### Modify

- `src/main/java/com/mycom/myapp/schedule/repository/StudyScheduleRepository.java`: 범위별 페이지와 그룹 제한 상세 조회
- `src/test/java/com/mycom/myapp/schedule/repository/StudyScheduleRepositoryTest.java`: 경계·정렬·그룹 격리 테스트
- `src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java`: 목록·상세 조회와 공통 접근 검증
- `src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java`: 조회 권한·범위·오류 테스트
- `src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java`: 목록·상세 GET API
- `src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java`: HTTP 입력·응답 계약 테스트
- `src/main/java/com/mycom/myapp/global/exception/ErrorCode.java`: `SCHEDULE_NOT_FOUND` 추가
- `docs/part3-group/api.md`: 일정 목록·상세 계약 추가
- `docs/part3-group/context.md`: 6단계 진행 상태와 다음 작업 갱신

---

### Task 1: 범위별 Repository 조회 기반

**Files:**
- Modify: `src/main/java/com/mycom/myapp/schedule/repository/StudyScheduleRepository.java`
- Test: `src/test/java/com/mycom/myapp/schedule/repository/StudyScheduleRepositoryTest.java`

**Interfaces:**
- Consumes: `StudySchedule.studyGroup.id`, `StudySchedule.scheduledAt`, `StudySchedule.id`
- Produces:
  - `Page<StudySchedule> findAllByStudyGroupIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(Long groupId, LocalDateTime scheduledAt, Pageable pageable)`
  - `Page<StudySchedule> findAllByStudyGroupIdAndScheduledAtLessThanOrderByScheduledAtDescIdDesc(Long groupId, LocalDateTime scheduledAt, Pageable pageable)`
  - `Optional<StudySchedule> findByIdAndStudyGroupId(Long id, Long groupId)`

- [ ] **Step 1: 예정·지난 일정의 경계와 정렬 실패 테스트 작성**

`StudyScheduleRepositoryTest`에 다음 import를 추가한다.

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
```

다음 테스트를 추가한다. 같은 시각 일정은 저장 순서대로 ID가 증가하므로 예정 목록은 ID 오름차순,
지난 목록은 ID 내림차순이어야 한다.

```java
@Test
void findsUpcomingSchedulesFromBoundaryWithStableAscendingOrder() {
    LocalDateTime boundary = LocalDateTime.of(2026, 7, 20, 12, 0);
    StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(20L, "알고리즘 스터디"));
    scheduleRepository.save(schedule(group, "지난 일정", boundary.minusSeconds(1)));
    StudySchedule sameTimeFirst =
            scheduleRepository.save(schedule(group, "동시 일정 1", boundary.plusHours(1)));
    StudySchedule sameTimeSecond =
            scheduleRepository.save(schedule(group, "동시 일정 2", boundary.plusHours(1)));
    scheduleRepository.saveAndFlush(schedule(group, "경계 일정", boundary));

    Page<StudySchedule> result =
            scheduleRepository
                    .findAllByStudyGroupIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(
                            group.getId(), boundary, PageRequest.of(0, 10));

    assertThat(result.getContent())
            .extracting(StudySchedule::getTitle)
            .containsExactly("경계 일정", "동시 일정 1", "동시 일정 2");
    assertThat(sameTimeFirst.getId()).isLessThan(sameTimeSecond.getId());
}

@Test
void findsPastSchedulesBeforeBoundaryWithStableDescendingOrder() {
    LocalDateTime boundary = LocalDateTime.of(2026, 7, 20, 12, 0);
    StudyGroup group = groupRepository.saveAndFlush(StudyGroup.create(21L, "백엔드 스터디"));
    scheduleRepository.save(schedule(group, "오래된 일정", boundary.minusDays(2)));
    scheduleRepository.save(schedule(group, "최근 동시 일정 1", boundary.minusHours(1)));
    scheduleRepository.save(schedule(group, "최근 동시 일정 2", boundary.minusHours(1)));
    scheduleRepository.saveAndFlush(schedule(group, "경계 일정", boundary));

    Page<StudySchedule> result =
            scheduleRepository
                    .findAllByStudyGroupIdAndScheduledAtLessThanOrderByScheduledAtDescIdDesc(
                            group.getId(), boundary, PageRequest.of(0, 10));

    assertThat(result.getContent())
            .extracting(StudySchedule::getTitle)
            .containsExactly("최근 동시 일정 2", "최근 동시 일정 1", "오래된 일정");
}
```

- [ ] **Step 2: 상세 조회의 그룹 격리 실패 테스트 작성**

```java
@Test
void findsScheduleOnlyInsideRequestedGroup() {
    StudyGroup requestedGroup =
            groupRepository.saveAndFlush(StudyGroup.create(22L, "요청 그룹"));
    StudyGroup otherGroup = groupRepository.saveAndFlush(StudyGroup.create(23L, "다른 그룹"));
    StudySchedule requestedSchedule =
            scheduleRepository.saveAndFlush(
                    schedule(
                            requestedGroup,
                            "요청 일정",
                            LocalDateTime.of(2026, 7, 25, 19, 0)));

    assertThat(
                    scheduleRepository.findByIdAndStudyGroupId(
                            requestedSchedule.getId(), requestedGroup.getId()))
            .contains(requestedSchedule);
    assertThat(
                    scheduleRepository.findByIdAndStudyGroupId(
                            requestedSchedule.getId(), otherGroup.getId()))
            .isEmpty();
}
```

- [ ] **Step 3: Repository 테스트가 컴파일 실패하는지 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.repository.StudyScheduleRepositoryTest"
```

Expected: `StudyScheduleRepository`에 새 메서드가 없어 `compileTestJava`가 실패한다.

- [ ] **Step 4: Repository 메서드 최소 구현**

`StudyScheduleRepository`를 다음과 같이 수정한다.

```java
package com.mycom.myapp.schedule.repository;

import com.mycom.myapp.schedule.entity.StudySchedule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyScheduleRepository extends JpaRepository<StudySchedule, Long> {

    List<StudySchedule> findAllByStudyGroupIdOrderByScheduledAtAsc(Long groupId);

    Page<StudySchedule>
            findAllByStudyGroupIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(
                    Long groupId, LocalDateTime scheduledAt, Pageable pageable);

    Page<StudySchedule> findAllByStudyGroupIdAndScheduledAtLessThanOrderByScheduledAtDescIdDesc(
            Long groupId, LocalDateTime scheduledAt, Pageable pageable);

    Optional<StudySchedule> findByIdAndStudyGroupId(Long id, Long groupId);
}
```

- [ ] **Step 5: Repository 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.repository.StudyScheduleRepositoryTest"
```

Expected: `BUILD SUCCESSFUL` and all `StudyScheduleRepositoryTest` tests pass.

- [ ] **Step 6: Repository 작업 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/repository/StudyScheduleRepository.java src/test/java/com/mycom/myapp/schedule/repository/StudyScheduleRepositoryTest.java
git commit -m "feat: 일정 범위별 조회 Repository 추가"
```

---

### Task 2: 조회 파라미터와 페이지 응답 DTO

**Files:**
- Create: `src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleScope.java`
- Create: `src/main/java/com/mycom/myapp/schedule/dto/request/ScheduleQueryRequest.java`
- Create: `src/main/java/com/mycom/myapp/schedule/dto/response/ScheduleSummaryResponse.java`
- Create: `src/main/java/com/mycom/myapp/schedule/dto/response/SchedulePageResponse.java`
- Test: `src/test/java/com/mycom/myapp/schedule/dto/request/ScheduleQueryRequestTest.java`
- Test: `src/test/java/com/mycom/myapp/schedule/dto/response/SchedulePageResponseTest.java`

**Interfaces:**
- Consumes: HTTP query 문자열 `scope`, `page`, `size`; `Page<StudySchedule>`
- Produces:
  - `ScheduleScope.from(String value)`
  - `ScheduleQueryRequest.from(String scope, String page, String size)`
  - `ScheduleSummaryResponse.from(StudySchedule schedule)`
  - `SchedulePageResponse.from(Page<StudySchedule> schedules)`

- [ ] **Step 1: 조회 파라미터 실패 테스트 작성**

`ScheduleQueryRequestTest.java`를 생성한다.

```java
package com.mycom.myapp.schedule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ScheduleQueryRequestTest {

    @Test
    void parsesSupportedScopeAndPageValues() {
        ScheduleQueryRequest request = ScheduleQueryRequest.from("past", "2", "30");

        assertThat(request.scope()).isEqualTo(ScheduleScope.PAST);
        assertThat(request.page()).isEqualTo(2);
        assertThat(request.size()).isEqualTo(30);
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void rejectsInvalidQuery(String scope, String page, String size) {
        assertThatThrownBy(() -> ScheduleQueryRequest.from(scope, page, size))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of("all", "0", "20"),
                Arguments.of("upcoming", "-1", "20"),
                Arguments.of("past", "zero", "20"),
                Arguments.of("past", "0", "0"),
                Arguments.of("past", "0", "101"));
    }
}
```

- [ ] **Step 2: 파라미터 테스트가 컴파일 실패하는지 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.dto.request.ScheduleQueryRequestTest"
```

Expected: `ScheduleQueryRequest`와 `ScheduleScope`가 없어 `compileTestJava`가 실패한다.

- [ ] **Step 3: 조회 파라미터 최소 구현**

`ScheduleScope.java`를 생성한다.

```java
package com.mycom.myapp.schedule.dto.request;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;

public enum ScheduleScope {
    UPCOMING,
    PAST;

    public static ScheduleScope from(String value) {
        if ("upcoming".equals(value)) {
            return UPCOMING;
        }
        if ("past".equals(value)) {
            return PAST;
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
}
```

`ScheduleQueryRequest.java`를 생성한다.

```java
package com.mycom.myapp.schedule.dto.request;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;

public record ScheduleQueryRequest(ScheduleScope scope, int page, int size) {

    public static ScheduleQueryRequest from(String scope, String page, String size) {
        int parsedPage;
        int parsedSize;
        try {
            parsedPage = Integer.parseInt(page);
            parsedSize = Integer.parseInt(size);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (parsedPage < 0 || parsedSize < 1 || parsedSize > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new ScheduleQueryRequest(ScheduleScope.from(scope), parsedPage, parsedSize);
    }
}
```

- [ ] **Step 4: 조회 파라미터 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.dto.request.ScheduleQueryRequestTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: 페이지 변환 실패 테스트 작성**

`SchedulePageResponseTest.java`를 생성한다.

```java
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

        assertThat(response.items()).singleElement().satisfies(item -> {
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
```

- [ ] **Step 6: 페이지 응답 테스트가 컴파일 실패하는지 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.dto.response.SchedulePageResponseTest"
```

Expected: 응답 DTO가 없어 `compileTestJava`가 실패한다.

- [ ] **Step 7: 목록 응답 DTO 최소 구현**

`ScheduleSummaryResponse.java`를 생성한다.

```java
package com.mycom.myapp.schedule.dto.response;

import com.mycom.myapp.schedule.entity.StudySchedule;
import java.time.LocalDateTime;

public record ScheduleSummaryResponse(
        Long scheduleId,
        Long creatorId,
        String title,
        LocalDateTime scheduledAt,
        String location,
        String onlineLink,
        LocalDateTime responseDeadline) {

    public static ScheduleSummaryResponse from(StudySchedule schedule) {
        return new ScheduleSummaryResponse(
                schedule.getId(),
                schedule.getCreatorId(),
                schedule.getTitle(),
                schedule.getScheduledAt(),
                schedule.getLocation(),
                schedule.getOnlineLink(),
                schedule.getResponseDeadline());
    }
}
```

`SchedulePageResponse.java`를 생성한다.

```java
package com.mycom.myapp.schedule.dto.response;

import com.mycom.myapp.schedule.entity.StudySchedule;
import java.util.List;
import org.springframework.data.domain.Page;

public record SchedulePageResponse(
        List<ScheduleSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static SchedulePageResponse from(Page<StudySchedule> schedules) {
        List<ScheduleSummaryResponse> items =
                schedules.getContent().stream().map(ScheduleSummaryResponse::from).toList();
        return new SchedulePageResponse(
                items,
                schedules.getNumber(),
                schedules.getSize(),
                schedules.getTotalElements(),
                schedules.getTotalPages(),
                schedules.hasNext());
    }
}
```

- [ ] **Step 8: DTO 테스트 전체 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.dto.*.*Test"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: DTO 작업 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/dto src/test/java/com/mycom/myapp/schedule/dto
git commit -m "feat: 일정 조회 요청과 페이지 응답 추가"
```

---

### Task 3: 일정 목록 Service

**Files:**
- Modify: `src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java`
- Test: `src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java`

**Interfaces:**
- Consumes: Task 1의 범위별 Repository 메서드, Task 2의 `ScheduleScope`와 `SchedulePageResponse`
- Produces: `SchedulePageResponse getSchedules(Long groupId, Long memberId, ScheduleScope scope, int page, int size)`

- [ ] **Step 1: 역할별 목록 조회와 범위 위임 실패 테스트 작성**

`ScheduleServiceTest`에 다음 import를 추가한다.

```java
import static org.mockito.Mockito.verifyNoInteractions;

import com.mycom.myapp.schedule.dto.request.ScheduleScope;
import com.mycom.myapp.schedule.dto.response.SchedulePageResponse;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
```

다음 테스트를 추가한다.

```java
@ParameterizedTest
@EnumSource(GroupRole.class)
void activeMemberOfAnyRoleCanReadUpcomingSchedules(GroupRole role) {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, role));
    PageRequest pageable = PageRequest.of(0, 20);
    StudySchedule schedule = schedule(group, 100L, NOW.plusDays(1));
    when(scheduleRepository
                    .findAllByStudyGroupIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(
                            10L, NOW, pageable))
            .thenReturn(new PageImpl<>(List.of(schedule), pageable, 1));

    SchedulePageResponse response =
            service.getSchedules(10L, 1L, ScheduleScope.UPCOMING, 0, 20);

    assertThat(response.items()).extracting(item -> item.scheduleId()).containsExactly(100L);
    verify(scheduleRepository)
            .findAllByStudyGroupIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(
                    10L, NOW, pageable);
}

@Test
void activeMemberCanReadPastSchedulesFromEndedGroup() {
    StudyGroup group = activeGroup();
    group.end();
    allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));
    PageRequest pageable = PageRequest.of(1, 10);
    when(scheduleRepository
                    .findAllByStudyGroupIdAndScheduledAtLessThanOrderByScheduledAtDescIdDesc(
                            10L, NOW, pageable))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    SchedulePageResponse response =
            service.getSchedules(10L, 1L, ScheduleScope.PAST, 1, 10);

    assertThat(response.items()).isEmpty();
    verify(scheduleRepository)
            .findAllByStudyGroupIdAndScheduledAtLessThanOrderByScheduledAtDescIdDesc(
                    10L, NOW, pageable);
}
```

테스트 하단에 다음 helper를 추가한다.

```java
private StudySchedule schedule(
        StudyGroup group, Long scheduleId, LocalDateTime scheduledAt) {
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
                    null);
    ReflectionTestUtils.setField(schedule, "id", scheduleId);
    return schedule;
}
```

- [ ] **Step 2: 조회 접근 오류 실패 테스트 작성**

```java
@Test
void rejectsScheduleListForMissingGroupBeforeMembershipLookup() {
    when(groupRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(
                    () -> service.getSchedules(10L, 1L, ScheduleScope.UPCOMING, 0, 20))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.GROUP_NOT_FOUND));
    verifyNoInteractions(memberRepository, scheduleRepository);
}

@Test
void rejectsScheduleListForMissingMembership() {
    StudyGroup group = activeGroup();
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(memberRepository.findByStudyGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(
                    () -> service.getSchedules(10L, 1L, ScheduleScope.UPCOMING, 0, 20))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.GROUP_ACCESS_DENIED));
    verifyNoInteractions(scheduleRepository);
}

@Test
void rejectsScheduleListForWithdrawnMember() {
    StudyGroup group = activeGroup();
    GroupMember member = GroupMember.join(group, 1L, GroupRole.MEMBER);
    member.withdraw();
    allow(group, member);

    assertThatThrownBy(
                    () -> service.getSchedules(10L, 1L, ScheduleScope.PAST, 0, 20))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.WITHDRAWN_GROUP_MEMBER));
    verifyNoInteractions(scheduleRepository);
}
```

- [ ] **Step 3: Service 테스트가 컴파일 실패하는지 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleServiceTest"
```

Expected: `getSchedules`가 없어 `compileTestJava`가 실패한다.

- [ ] **Step 4: 공통 접근 검증을 추출하고 목록 조회 최소 구현**

`ScheduleService`에 다음 import를 추가한다.

```java
import com.mycom.myapp.schedule.dto.request.ScheduleScope;
import com.mycom.myapp.schedule.dto.response.SchedulePageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
```

기존 `create` 메서드의 그룹·그룹원 조회 블록을 다음 두 호출로 교체한다.

```java
StudyGroup group = getGroup(groupId);
GroupMember member = getActiveMember(groupId, memberId);
```

클래스에 목록 메서드를 추가한다.

```java
@Transactional(readOnly = true)
public SchedulePageResponse getSchedules(
        Long groupId, Long memberId, ScheduleScope scope, int page, int size) {
    getGroup(groupId);
    getActiveMember(groupId, memberId);
    LocalDateTime now = LocalDateTime.now(clock);
    PageRequest pageable = PageRequest.of(page, size);
    Page<StudySchedule> schedules =
            switch (scope) {
                case UPCOMING ->
                        scheduleRepository
                                .findAllByStudyGroupIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(
                                        groupId, now, pageable);
                case PAST ->
                        scheduleRepository
                                .findAllByStudyGroupIdAndScheduledAtLessThanOrderByScheduledAtDescIdDesc(
                                        groupId, now, pageable);
            };
    return SchedulePageResponse.from(schedules);
}
```

클래스 하단에 공통 helper를 추가한다.

```java
private StudyGroup getGroup(Long groupId) {
    return groupRepository
            .findById(groupId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
}

private GroupMember getActiveMember(Long groupId, Long memberId) {
    GroupMember member =
            memberRepository
                    .findByStudyGroupIdAndUserId(groupId, memberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));
    if (member.getStatus() == GroupMemberStatus.WITHDRAWN) {
        throw new BusinessException(ErrorCode.WITHDRAWN_GROUP_MEMBER);
    }
    return member;
}
```

- [ ] **Step 5: 기존 생성과 새 목록 Service 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleServiceTest"
```

Expected: `BUILD SUCCESSFUL`; 기존 생성 검증 순서 테스트와 새 목록 테스트가 모두 통과한다.

- [ ] **Step 6: 목록 Service 작업 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java
git commit -m "feat: 권한 기반 일정 목록 조회 추가"
```

---

### Task 4: 일정 상세 Service와 오류 계약

**Files:**
- Modify: `src/main/java/com/mycom/myapp/global/exception/ErrorCode.java`
- Modify: `src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java`
- Test: `src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java`

**Interfaces:**
- Consumes: Task 1의 `findByIdAndStudyGroupId`, 기존 `ScheduleResponse.from`
- Produces: `ScheduleResponse getSchedule(Long groupId, Long memberId, Long scheduleId)`

- [ ] **Step 1: 상세 조회 성공과 그룹 격리 실패 테스트 작성**

```java
@Test
void returnsFullScheduleDetailForActiveMember() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));
    StudySchedule schedule = schedule(group, 100L, NOW.minusDays(1));
    when(scheduleRepository.findByIdAndStudyGroupId(100L, 10L))
            .thenReturn(Optional.of(schedule));

    ScheduleResponse response = service.getSchedule(10L, 1L, 100L);

    assertThat(response.scheduleId()).isEqualTo(100L);
    assertThat(response.groupId()).isEqualTo(10L);
    assertThat(response.title()).isEqualTo("조회 일정");
}

@Test
void reportsScheduleNotFoundWhenScheduleIsMissingOrBelongsToAnotherGroup() {
    StudyGroup group = activeGroup();
    allow(group, GroupMember.join(group, 1L, GroupRole.MEMBER));
    when(scheduleRepository.findByIdAndStudyGroupId(999L, 10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSchedule(10L, 1L, 999L))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND));
}
```

- [ ] **Step 2: 상세 Service 테스트가 컴파일 실패하는지 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleServiceTest"
```

Expected: `getSchedule` 또는 `SCHEDULE_NOT_FOUND`가 없어 컴파일이 실패한다.

- [ ] **Step 3: `SCHEDULE_NOT_FOUND` 추가**

`ErrorCode`의 일정 오류 항목에 다음 값을 추가한다.

```java
SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
```

- [ ] **Step 4: 상세 조회 최소 구현**

`ScheduleService`에 다음 메서드를 추가한다.

```java
@Transactional(readOnly = true)
public ScheduleResponse getSchedule(Long groupId, Long memberId, Long scheduleId) {
    getGroup(groupId);
    getActiveMember(groupId, memberId);
    StudySchedule schedule =
            scheduleRepository
                    .findByIdAndStudyGroupId(scheduleId, groupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    return ScheduleResponse.from(schedule);
}
```

- [ ] **Step 5: 상세 조회와 전체 Service 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleServiceTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: 상세 Service 작업 커밋**

```powershell
git add src/main/java/com/mycom/myapp/global/exception/ErrorCode.java src/main/java/com/mycom/myapp/schedule/service/ScheduleService.java src/test/java/com/mycom/myapp/schedule/service/ScheduleServiceTest.java
git commit -m "feat: 그룹 일정 상세 조회 추가"
```

---

### Task 5: 일정 목록·상세 Controller

**Files:**
- Modify: `src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java`
- Test: `src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java`

**Interfaces:**
- Consumes:
  - `ScheduleQueryRequest.from(String scope, String page, String size)`
  - `ScheduleService.getSchedules(...)`
  - `ScheduleService.getSchedule(...)`
- Produces:
  - `GET /api/groups/{groupId}/schedules`
  - `GET /api/groups/{groupId}/schedules/{scheduleId}`

- [ ] **Step 1: 기본 목록과 지난 일정 HTTP 실패 테스트 작성**

`ScheduleControllerTest`에 다음 static import와 타입 import를 추가한다.

```java
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.mycom.myapp.schedule.dto.request.ScheduleScope;
import com.mycom.myapp.schedule.dto.response.SchedulePageResponse;
import com.mycom.myapp.schedule.dto.response.ScheduleSummaryResponse;
import java.util.List;
```

다음 테스트와 fixture를 추가한다.

```java
@Test
void listsUpcomingSchedulesWithDefaultPageParameters() throws Exception {
    when(scheduleService.getSchedules(10L, 1L, ScheduleScope.UPCOMING, 0, 20))
            .thenReturn(pageResponse());

    mockMvc.perform(get("/api/groups/{groupId}/schedules", 10L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].scheduleId").value(100))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    verify(scheduleService).getSchedules(10L, 1L, ScheduleScope.UPCOMING, 0, 20);
}

@Test
void listsPastSchedulesWithRequestedPageParameters() throws Exception {
    when(scheduleService.getSchedules(10L, 1L, ScheduleScope.PAST, 2, 10))
            .thenReturn(new SchedulePageResponse(List.of(), 2, 10, 0, 0, false));

    mockMvc.perform(
                    get("/api/groups/{groupId}/schedules", 10L)
                            .queryParam("scope", "past")
                            .queryParam("page", "2")
                            .queryParam("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.page").value(2));
    verify(scheduleService).getSchedules(10L, 1L, ScheduleScope.PAST, 2, 10);
}

private static SchedulePageResponse pageResponse() {
    ScheduleSummaryResponse item =
            new ScheduleSummaryResponse(
                    100L,
                    1L,
                    "3주차 스터디",
                    LocalDateTime.of(2026, 7, 25, 19, 0),
                    null,
                    null,
                    LocalDateTime.of(2026, 7, 24, 18, 0));
    return new SchedulePageResponse(List.of(item), 0, 20, 1, 1, false);
}
```

- [ ] **Step 2: 잘못된 목록 파라미터 HTTP 실패 테스트 작성**

```java
@ParameterizedTest
@MethodSource("invalidListQueries")
void rejectsInvalidListQuery(String scope, String page, String size) throws Exception {
    mockMvc.perform(
                    get("/api/groups/{groupId}/schedules", 10L)
                            .queryParam("scope", scope)
                            .queryParam("page", page)
                            .queryParam("size", size))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST.getMessage()));
    verifyNoMoreInteractions(scheduleService);
}

private static Stream<Arguments> invalidListQueries() {
    return Stream.of(
            Arguments.of("all", "0", "20"),
            Arguments.of("upcoming", "-1", "20"),
            Arguments.of("past", "page", "20"),
            Arguments.of("past", "0", "0"),
            Arguments.of("past", "0", "101"));
}
```

`Arguments` import를 추가한다.

```java
import org.junit.jupiter.params.provider.Arguments;
```

- [ ] **Step 3: 상세 조회 HTTP 실패 테스트 작성**

```java
@Test
void returnsFullScheduleDetail() throws Exception {
    when(scheduleService.getSchedule(10L, 1L, 100L)).thenReturn(response());

    mockMvc.perform(get("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 100L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.scheduleId").value(100))
            .andExpect(jsonPath("$.data.groupId").value(10))
            .andExpect(jsonPath("$.data.content").value("3장 문제 풀이"));
    verify(scheduleService).getSchedule(10L, 1L, 100L);
}

@Test
void mapsScheduleNotFoundOnDetail() throws Exception {
    when(scheduleService.getSchedule(10L, 1L, 999L))
            .thenThrow(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));

    mockMvc.perform(get("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ErrorCode.SCHEDULE_NOT_FOUND.getMessage()));
}

@Test
void rejectsMissingPrincipalForScheduleList() {
    ScheduleController controller = new ScheduleController(scheduleService);

    assertThatThrownBy(
                    () -> controller.getSchedules(10L, null, "upcoming", "0", "20"))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.UNAUTHORIZED));
}

@Test
void rejectsMissingPrincipalForScheduleDetail() {
    ScheduleController controller = new ScheduleController(scheduleService);

    assertThatThrownBy(() -> controller.getSchedule(10L, 100L, null))
            .isInstanceOfSatisfying(
                    BusinessException.class,
                    exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.UNAUTHORIZED));
}
```

- [ ] **Step 4: Controller 테스트가 실패하는지 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.controller.ScheduleControllerTest"
```

Expected: GET 매핑이 없어 405 또는 컴파일 실패가 발생한다.

- [ ] **Step 5: GET 매핑 최소 구현**

`ScheduleController`에 다음 import를 추가한다.

```java
import com.mycom.myapp.schedule.dto.request.ScheduleQueryRequest;
import com.mycom.myapp.schedule.dto.response.SchedulePageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
```

클래스에 다음 메서드를 추가한다.

```java
@GetMapping
public ApiResponse<SchedulePageResponse> getSchedules(
        @PathVariable Long groupId,
        @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
        @RequestParam(defaultValue = "upcoming") String scope,
        @RequestParam(defaultValue = "0") String page,
        @RequestParam(defaultValue = "20") String size) {
    Long memberId = requireAuthenticatedMemberId(authenticatedMember);
    ScheduleQueryRequest query = ScheduleQueryRequest.from(scope, page, size);
    return ApiResponse.success(
            scheduleService.getSchedules(
                    groupId, memberId, query.scope(), query.page(), query.size()));
}

@GetMapping("/{scheduleId}")
public ApiResponse<ScheduleResponse> getSchedule(
        @PathVariable Long groupId,
        @PathVariable Long scheduleId,
        @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
    Long memberId = requireAuthenticatedMemberId(authenticatedMember);
    return ApiResponse.success(scheduleService.getSchedule(groupId, memberId, scheduleId));
}

private Long requireAuthenticatedMemberId(AuthenticatedMember authenticatedMember) {
    if (authenticatedMember == null) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    return authenticatedMember.id();
}
```

기존 `create` 메서드의 Principal null 검사와 `authenticatedMember.id()` 사용을 다음처럼 바꾼다.

```java
Long memberId = requireAuthenticatedMemberId(authenticatedMember);
ScheduleResponse response = scheduleService.create(groupId, memberId, request);
```

- [ ] **Step 6: Controller 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.controller.ScheduleControllerTest"
```

Expected: `BUILD SUCCESSFUL`; 기존 POST 테스트와 새 GET 테스트가 모두 통과한다.

- [ ] **Step 7: Controller 작업 커밋**

```powershell
git add src/main/java/com/mycom/myapp/schedule/controller/ScheduleController.java src/test/java/com/mycom/myapp/schedule/controller/ScheduleControllerTest.java
git commit -m "feat: 일정 목록과 상세 조회 API 추가"
```

---

### Task 6: 통합 흐름과 API 문서 동기화

**Files:**
- Create: `src/test/java/com/mycom/myapp/schedule/service/ScheduleQueryIntegrationTest.java`
- Modify: `docs/part3-group/api.md`
- Modify: `docs/part3-group/context.md`

**Interfaces:**
- Consumes: Tasks 1~5의 Repository, DTO, Service, Controller 계약
- Produces: 실제 영속성 기반 일정 조회 검증과 구현과 일치하는 문서 계약

- [ ] **Step 1: 실제 영속성 기반 통합 테스트 작성**

`ScheduleQueryIntegrationTest.java`를 생성한다.

```java
package com.mycom.myapp.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

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
        org.mockito.Mockito.when(clock.instant()).thenReturn(NOW.atZone(ZONE).toInstant());
        org.mockito.Mockito.when(clock.getZone()).thenReturn(ZONE);
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
                StudySchedule.create(
                        group,
                        1L,
                        title,
                        scheduledAt,
                        null,
                        null,
                        null,
                        null,
                        null));
    }
}
```

- [ ] **Step 2: 통합 테스트 실행**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.service.ScheduleQueryIntegrationTest"
```

Expected: `BUILD SUCCESSFUL`; 예정 2건과 지난 일정 2건이 누락·중복 없이 조회된다.

- [ ] **Step 3: `api.md` 엔드포인트 목록과 상세 계약 갱신**

엔드포인트 표에 다음 두 행을 추가한다.

```markdown
| 일정 목록 조회 | `GET` | `/api/groups/{groupId}/schedules` | 필수 |
| 일정 상세 조회 | `GET` | `/api/groups/{groupId}/schedules/{scheduleId}` | 필수 |
```

`schedule-query-design.md`와 동일하게 다음 내용을 문서화한다.

- `scope=upcoming|past`, `page=0`, `size=20`, 최대 `size=100`
- 예정 `scheduledAt >= now` 오름차순, 지난 `scheduledAt < now` 내림차순
- 동일 시각 `scheduleId` 보조 정렬
- `ScheduleSummaryResponse` 필드 7개와 `SchedulePageResponse` 메타데이터 6개
- 상세 조회의 기존 `ScheduleResponse` 재사용
- 종료 그룹 조회 허용과 활성 그룹원 접근 규칙
- `SCHEDULE_NOT_FOUND`를 포함한 오류 표

- [ ] **Step 4: `context.md`를 실제 구현 상태로 갱신**

다음 항목을 반영한다.

- 6단계를 `구현 완료, 검증 중`으로 변경
- 완료된 Repository, DTO, Service, Controller와 통합 테스트 기록
- 현재 브랜치의 최신 HEAD 기록
- 바로 다음 작업을 전체 테스트, Spotless, 구현 대조와 PR 준비로 변경

- [ ] **Step 5: 문서와 통합 테스트 커밋**

```powershell
git add src/test/java/com/mycom/myapp/schedule/service/ScheduleQueryIntegrationTest.java docs/part3-group/api.md docs/part3-group/context.md
git commit -m "test: 일정 조회 통합 흐름과 API 계약 검증"
```

---

### Task 7: 전체 검증과 완료 상태 기록

**Files:**
- Modify only if verification reveals an in-scope defect: `src/main/java/com/mycom/myapp/schedule/**`, `src/test/java/com/mycom/myapp/schedule/**`, `docs/part3-group/**`
- Modify only for the agreed error: `src/main/java/com/mycom/myapp/global/exception/ErrorCode.java`

**Interfaces:**
- Consumes: Tasks 1~6의 완성된 변경
- Produces: CI와 동일한 포맷·전체 테스트 검증 결과

- [ ] **Step 1: 일정 패키지 관련 테스트 재실행**

Run:

```powershell
.\gradlew.bat test --tests "com.mycom.myapp.schedule.*"
```

Expected: `BUILD SUCCESSFUL` and no failed schedule tests.

- [ ] **Step 2: 전체 테스트 실행**

Run:

```powershell
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL` and zero failed tests.

- [ ] **Step 3: Spotless 검사 실행**

Run:

```powershell
.\gradlew.bat spotlessCheck
```

Expected: `BUILD SUCCESSFUL`.

실패하면 다음 명령으로 포맷을 적용한 뒤 관련 테스트와 `spotlessCheck`를 다시 실행한다.

```powershell
.\gradlew.bat spotlessApply
```

- [ ] **Step 4: 변경 범위와 공백 오류 확인**

Run:

```powershell
git status --short
git diff --check
git diff --stat develop...HEAD
```

Expected:

- Part3 소유 경로와 합의된 `ErrorCode.java` 외 변경이 없다.
- `git diff --check` 출력이 없다.
- 개인 환경 설정 파일이 포함되지 않는다.

- [ ] **Step 5: 검증 후 작업 트리 확인**

Run:

```powershell
git status --short
```

Expected: 출력이 없다. 출력이 있으면 작업을 완료로 표시하지 말고 해당 변경을 만든 Task의
테스트 단계로 돌아가 실패를 재현한 뒤 그 Task의 파일과 커밋 메시지로 수정한다.

- [ ] **Step 6: PR 준비 정보 기록**

PR 제목 권장안:

```text
feat: 그룹 일정 목록과 상세 조회 추가
```

PR 본문에는 다음 내용을 기록한다.

```markdown
## 목적
- 활성 그룹원이 예정·지난 일정과 일정 상세를 조회할 수 있도록 합니다.

## 변경 범위
- 범위별 일정 페이지 Repository 조회
- 예정·지난 목록과 상세 조회 Service/Controller
- 명시적인 목록·페이지 응답 DTO
- `SCHEDULE_NOT_FOUND` 오류 계약
- 계층별 테스트와 통합 테스트

## 접근 및 정렬 규칙
- 활성 그룹원은 역할과 그룹 종료 여부에 관계없이 조회할 수 있습니다.
- 예정 일정은 시간·ID 오름차순, 지난 일정은 시간·ID 내림차순입니다.
- 상세 조회는 요청한 그룹에 속한 일정만 반환합니다.

## 검증
- `./gradlew.bat test`
- `./gradlew.bat spotlessCheck`
```
