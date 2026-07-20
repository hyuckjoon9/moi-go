# 그룹 생성 유스케이스 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Part2가 전달한 모집 결과로 그룹과 초기 그룹원을 한 트랜잭션에서 생성하고, 순차·동시 재요청에는 기존 그룹 ID를 반환한다.

**Architecture:** 공개 `StudyGroupCreationService`가 기존 그룹 조회와 동시성 충돌 복구를 담당하고, 별도 Spring Bean인 `StudyGroupCreationWriter`가 실제 저장 트랜잭션을 담당한다. 입력은 불변 `CreateStudyGroupCommand`로 검증하며 `study_groups.post_id` 유니크 제약을 최종 동시성 경계로 사용한다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Data JPA, Hibernate, JUnit 5, AssertJ, Mockito, H2 MySQL mode

## Global Constraints

- 수정 범위는 `src/main/java/com/mycom/myapp/study/**`, 대응 테스트와 `docs/part3-group/**`로 제한한다.
- Part2가 Part3의 공개 서비스를 직접 호출하며 HTTP Controller는 추가하지 않는다.
- Part1·Part2 Entity 또는 Repository를 참조하거나 수정하지 않는다.
- 외부 모집글과 사용자 존재 여부는 호출자인 Part2가 보장한다.
- `postId`가 같은 재요청은 최초 생성 결과를 유지하고 기존 그룹 ID를 반환한다.
- 모집장은 항상 `LEADER`, 모집장을 제외한 승인 회원은 `MEMBER`로 등록한다.
- 승인 회원 ID의 입력 순서를 유지하면서 중복을 제거한다.
- 모든 운영 코드 변경은 실패하는 테스트를 먼저 확인한 뒤 작성한다.

---

### Task 1: 그룹 생성 명령 계약

**Files:**
- Create: `src/main/java/com/mycom/myapp/study/service/CreateStudyGroupCommand.java`
- Create: `src/test/java/com/mycom/myapp/study/service/CreateStudyGroupCommandTest.java`

**Interfaces:**
- Produces: `new CreateStudyGroupCommand(Long postId, String groupName, Long leaderUserId, List<Long> approvedUserIds)`
- Produces: `Long postId()`, `String groupName()`, `Long leaderUserId()`, `List<Long> approvedUserIds()`

- [ ] **Step 1: 명령의 정상화와 방어적 복사 실패 테스트 작성**

```java
package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateStudyGroupCommandTest {

    @Test
    void normalizesNameAndDefensivelyCopiesApprovedUsers() {
        List<Long> approvedUserIds = new ArrayList<>(List.of(20L, 30L));

        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "  알고리즘 스터디  ", 1L, approvedUserIds);
        approvedUserIds.add(40L);

        assertThat(command.postId()).isEqualTo(10L);
        assertThat(command.groupName()).isEqualTo("알고리즘 스터디");
        assertThat(command.leaderUserId()).isEqualTo(1L);
        assertThat(command.approvedUserIds()).containsExactly(20L, 30L);
    }

    @Test
    void acceptsEmptyApprovedUserList() {
        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of());

        assertThat(command.approvedUserIds()).isEmpty();
    }
}
```

- [ ] **Step 2: 정상 입력 테스트 실패 확인**

Run: `.\gradlew.bat test --tests "com.mycom.myapp.study.service.CreateStudyGroupCommandTest"`

Expected: `CreateStudyGroupCommand`가 없어 컴파일 실패

- [ ] **Step 3: 명령의 필수값 검증 실패 테스트 추가**

```java
package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateStudyGroupCommandTest {

    @Test
    void normalizesNameAndDefensivelyCopiesApprovedUsers() {
        List<Long> approvedUserIds = new ArrayList<>(List.of(20L, 30L));

        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "  알고리즘 스터디  ", 1L, approvedUserIds);
        approvedUserIds.add(40L);

        assertThat(command.postId()).isEqualTo(10L);
        assertThat(command.groupName()).isEqualTo("알고리즘 스터디");
        assertThat(command.leaderUserId()).isEqualTo(1L);
        assertThat(command.approvedUserIds()).containsExactly(20L, 30L);
    }

    @Test
    void acceptsEmptyApprovedUserList() {
        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of());

        assertThat(command.approvedUserIds()).isEmpty();
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new CreateStudyGroupCommand(
                                        null, "알고리즘 스터디", 1L, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CreateStudyGroupCommand(10L, " ", 1L, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new CreateStudyGroupCommand(
                                        10L, "알고리즘 스터디", null, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, null));
    }

    @Test
    void rejectsNullApprovedUserId() {
        List<Long> approvedUserIds = new ArrayList<>();
        approvedUserIds.add(20L);
        approvedUserIds.add(null);

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new CreateStudyGroupCommand(
                                        10L, "알고리즘 스터디", 1L, approvedUserIds));
    }
}
```

- [ ] **Step 4: 명령 최소 구현**

```java
package com.mycom.myapp.study.service;

import java.util.List;

public record CreateStudyGroupCommand(
        Long postId, String groupName, Long leaderUserId, List<Long> approvedUserIds) {

    public CreateStudyGroupCommand {
        if (postId == null) {
            throw new IllegalArgumentException("모집글 식별자는 필수입니다.");
        }
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("그룹 이름은 필수입니다.");
        }
        if (leaderUserId == null) {
            throw new IllegalArgumentException("모집장 식별자는 필수입니다.");
        }
        if (approvedUserIds == null) {
            throw new IllegalArgumentException("승인 회원 목록은 필수입니다.");
        }
        if (approvedUserIds.stream().anyMatch(userId -> userId == null)) {
            throw new IllegalArgumentException("승인 회원 식별자는 null일 수 없습니다.");
        }

        groupName = groupName.strip();
        approvedUserIds = List.copyOf(approvedUserIds);
    }
}
```

- [ ] **Step 5: 명령 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.mycom.myapp.study.service.CreateStudyGroupCommandTest"`

Expected: 모든 `CreateStudyGroupCommandTest` 테스트 PASS

- [ ] **Step 6: 명령 계약 커밋**

```powershell
git add src/main/java/com/mycom/myapp/study/service/CreateStudyGroupCommand.java src/test/java/com/mycom/myapp/study/service/CreateStudyGroupCommandTest.java
git commit -m "feat: 그룹 생성 명령 계약 추가"
```

---

### Task 2: 초기 그룹원 저장 트랜잭션

**Files:**
- Create: `src/main/java/com/mycom/myapp/study/service/StudyGroupCreationWriter.java`
- Create: `src/test/java/com/mycom/myapp/study/service/StudyGroupCreationWriterTest.java`

**Interfaces:**
- Consumes: Task 1의 `CreateStudyGroupCommand`
- Consumes: `StudyGroup.create(Long postId, String name)`, `GroupMember.join(StudyGroup studyGroup, Long userId, GroupRole role)`
- Produces: package-private Writer Bean의 프록시 가능 `public Long create(CreateStudyGroupCommand command)`

- [ ] **Step 1: 리더와 승인 회원 저장 실패 테스트 작성**

```java
package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StudyGroupCreationWriterTest {

    private final StudyGroupRepository studyGroupRepository = mock(StudyGroupRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final StudyGroupCreationWriter writer =
            new StudyGroupCreationWriter(studyGroupRepository, groupMemberRepository);

    @Test
    void savesLeaderAndDistinctApprovedMembersInInputOrder() {
        StudyGroup savedGroup = mock(StudyGroup.class);
        when(savedGroup.getId()).thenReturn(100L);
        when(studyGroupRepository.saveAndFlush(any(StudyGroup.class))).thenReturn(savedGroup);
        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(
                        10L, "알고리즘 스터디", 1L, List.of(20L, 1L, 20L, 30L));

        Long groupId = writer.create(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GroupMember>> membersCaptor = ArgumentCaptor.forClass(List.class);
        verify(groupMemberRepository).saveAll(membersCaptor.capture());
        verify(groupMemberRepository).flush();
        assertThat(groupId).isEqualTo(100L);
        assertThat(membersCaptor.getValue())
                .extracting(GroupMember::getUserId, GroupMember::getRole)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, GroupRole.LEADER),
                        org.assertj.core.groups.Tuple.tuple(20L, GroupRole.MEMBER),
                        org.assertj.core.groups.Tuple.tuple(30L, GroupRole.MEMBER));
    }

    @Test
    void savesOnlyLeaderWhenApprovedMemberListIsEmpty() {
        StudyGroup savedGroup = mock(StudyGroup.class);
        when(savedGroup.getId()).thenReturn(100L);
        when(studyGroupRepository.saveAndFlush(any(StudyGroup.class))).thenReturn(savedGroup);
        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of());

        writer.create(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GroupMember>> membersCaptor = ArgumentCaptor.forClass(List.class);
        verify(groupMemberRepository).saveAll(membersCaptor.capture());
        assertThat(membersCaptor.getValue())
                .singleElement()
                .satisfies(
                        member -> {
                            assertThat(member.getUserId()).isEqualTo(1L);
                            assertThat(member.getRole()).isEqualTo(GroupRole.LEADER);
                        });
    }
}
```

- [ ] **Step 2: Writer 테스트 실패 확인**

Run: `.\gradlew.bat test --tests "com.mycom.myapp.study.service.StudyGroupCreationWriterTest"`

Expected: `StudyGroupCreationWriter`가 없어 컴파일 실패

- [ ] **Step 3: Writer 최소 구현**

```java
package com.mycom.myapp.study.service;

import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StudyGroupCreationWriter {

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    StudyGroupCreationWriter(
            StudyGroupRepository studyGroupRepository,
            GroupMemberRepository groupMemberRepository) {
        this.studyGroupRepository = studyGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Transactional
    public Long create(CreateStudyGroupCommand command) {
        StudyGroup group =
                studyGroupRepository.saveAndFlush(
                        StudyGroup.create(command.postId(), command.groupName()));

        List<GroupMember> members = new ArrayList<>();
        members.add(GroupMember.join(group, command.leaderUserId(), GroupRole.LEADER));

        Set<Long> distinctApprovedUserIds = new LinkedHashSet<>(command.approvedUserIds());
        distinctApprovedUserIds.remove(command.leaderUserId());
        distinctApprovedUserIds.forEach(
                userId -> members.add(GroupMember.join(group, userId, GroupRole.MEMBER)));

        groupMemberRepository.saveAll(members);
        groupMemberRepository.flush();
        return group.getId();
    }
}
```

- [ ] **Step 4: Writer 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.mycom.myapp.study.service.StudyGroupCreationWriterTest"`

Expected: 리더 우선 저장, 중복 제거, 입력 순서 유지와 빈 회원 목록 테스트 PASS

- [ ] **Step 5: Writer 커밋**

```powershell
git add src/main/java/com/mycom/myapp/study/service/StudyGroupCreationWriter.java src/test/java/com/mycom/myapp/study/service/StudyGroupCreationWriterTest.java
git commit -m "feat: 초기 그룹원 저장 트랜잭션 추가"
```

---

### Task 3: 멱등 그룹 생성 서비스

**Files:**
- Create: `src/main/java/com/mycom/myapp/study/service/StudyGroupCreationService.java`
- Create: `src/test/java/com/mycom/myapp/study/service/StudyGroupCreationServiceTest.java`

**Interfaces:**
- Consumes: Task 1의 `CreateStudyGroupCommand`
- Consumes: Task 2의 `StudyGroupCreationWriter.create(CreateStudyGroupCommand)`
- Produces: `public Long StudyGroupCreationService.create(CreateStudyGroupCommand command)`

- [ ] **Step 1: 기존 그룹과 신규 그룹 분기 실패 테스트 작성**

```java
package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StudyGroupCreationServiceTest {

    private final StudyGroupRepository studyGroupRepository = mock(StudyGroupRepository.class);
    private final StudyGroupCreationWriter writer = mock(StudyGroupCreationWriter.class);
    private final StudyGroupCreationService service =
            new StudyGroupCreationService(studyGroupRepository, writer);

    private final CreateStudyGroupCommand command =
            new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of(20L));

    @Test
    void returnsExistingGroupIdWithoutWritingAgain() {
        StudyGroup existingGroup = mock(StudyGroup.class);
        when(existingGroup.getId()).thenReturn(100L);
        when(studyGroupRepository.findByPostId(10L)).thenReturn(Optional.of(existingGroup));

        Long groupId = service.create(command);

        assertThat(groupId).isEqualTo(100L);
        verify(writer, never()).create(command);
    }

    @Test
    void returnsNewGroupIdFromWriter() {
        when(studyGroupRepository.findByPostId(10L)).thenReturn(Optional.empty());
        when(writer.create(command)).thenReturn(100L);

        Long groupId = service.create(command);

        assertThat(groupId).isEqualTo(100L);
        verify(writer).create(command);
    }
}
```

- [ ] **Step 2: 서비스 테스트 실패 확인**

Run: `.\gradlew.bat test --tests "com.mycom.myapp.study.service.StudyGroupCreationServiceTest"`

Expected: `StudyGroupCreationService`가 없어 컴파일 실패

- [ ] **Step 3: 동시성 충돌 복구와 무결성 오류 전파 실패 테스트 추가**

아래 두 테스트를 `StudyGroupCreationServiceTest`에 추가한다.

```java
@Test
void returnsExistingGroupIdAfterConcurrentUniqueConflict() {
    StudyGroup existingGroup = mock(StudyGroup.class);
    when(existingGroup.getId()).thenReturn(100L);
    when(studyGroupRepository.findByPostId(10L))
            .thenReturn(Optional.empty(), Optional.of(existingGroup));
    when(writer.create(command)).thenThrow(new DataIntegrityViolationException("duplicate post"));

    Long groupId = service.create(command);

    assertThat(groupId).isEqualTo(100L);
}

@Test
void propagatesIntegrityFailureWhenNoConcurrentGroupExists() {
    DataIntegrityViolationException failure =
            new DataIntegrityViolationException("foreign key violation");
    when(studyGroupRepository.findByPostId(10L)).thenReturn(Optional.empty());
    when(writer.create(command)).thenThrow(failure);

    assertThatThrownBy(() -> service.create(command)).isSameAs(failure);
}
```

다음 정적 import와 타입 import를 추가한다.

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.dao.DataIntegrityViolationException;
```

- [ ] **Step 4: 공개 서비스 최소 구현**

```java
package com.mycom.myapp.study.service;

import com.mycom.myapp.study.repository.StudyGroupRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class StudyGroupCreationService {

    private final StudyGroupRepository studyGroupRepository;
    private final StudyGroupCreationWriter writer;

    StudyGroupCreationService(
            StudyGroupRepository studyGroupRepository, StudyGroupCreationWriter writer) {
        this.studyGroupRepository = studyGroupRepository;
        this.writer = writer;
    }

    public Long create(CreateStudyGroupCommand command) {
        return studyGroupRepository
                .findByPostId(command.postId())
                .map(group -> group.getId())
                .orElseGet(() -> createNewOrReturnConcurrentGroup(command));
    }

    private Long createNewOrReturnConcurrentGroup(CreateStudyGroupCommand command) {
        try {
            return writer.create(command);
        } catch (DataIntegrityViolationException failure) {
            return studyGroupRepository
                    .findByPostId(command.postId())
                    .map(group -> group.getId())
                    .orElseThrow(() -> failure);
        }
    }
}
```

- [ ] **Step 5: 서비스 단위 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.mycom.myapp.study.service.StudyGroupCreationServiceTest"`

Expected: 기존 조회, 신규 생성, 동시 충돌 복구와 무결성 오류 전파 테스트 PASS

- [ ] **Step 6: 공개 서비스 커밋**

```powershell
git add src/main/java/com/mycom/myapp/study/service/StudyGroupCreationService.java src/test/java/com/mycom/myapp/study/service/StudyGroupCreationServiceTest.java
git commit -m "feat: 멱등 그룹 생성 서비스 추가"
```

---

### Task 4: 트랜잭션·동시성 통합 검증

**Files:**
- Create: `src/test/java/com/mycom/myapp/study/service/StudyGroupCreationIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3의 `StudyGroupCreationService.create(CreateStudyGroupCommand)`
- Verifies: 실제 Spring 트랜잭션, JPA 저장 결과, 유니크 제약 충돌 복구와 전체 롤백

- [ ] **Step 1: 실제 저장과 순차 멱등성 통합 테스트 작성**

```java
package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:group_creation;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class StudyGroupCreationIntegrationTest {

    @Autowired private StudyGroupCreationService service;
    @Autowired private StudyGroupRepository studyGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;

    @BeforeEach
    void cleanUp() {
        groupMemberRepository.deleteAll();
        studyGroupRepository.deleteAll();
    }

    @Test
    void createsGroupWithNormalizedInitialMembers() {
        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(
                        10L, "알고리즘 스터디", 1L, List.of(20L, 1L, 20L, 30L));

        Long groupId = service.create(command);

        assertThat(studyGroupRepository.findByPostId(10L))
                .get()
                .extracting(group -> group.getId(), group -> group.getName())
                .containsExactly(groupId, "알고리즘 스터디");
        assertThat(groupMemberRepository.findAllByStudyGroupId(groupId))
                .extracting(member -> member.getUserId(), member -> member.getRole())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, GroupRole.LEADER),
                        org.assertj.core.groups.Tuple.tuple(20L, GroupRole.MEMBER),
                        org.assertj.core.groups.Tuple.tuple(30L, GroupRole.MEMBER));
    }

    @Test
    void repeatedRequestReturnsSameGroupWithoutAddingMembers() {
        CreateStudyGroupCommand first =
                new CreateStudyGroupCommand(10L, "최초 이름", 1L, List.of(20L));
        CreateStudyGroupCommand retry =
                new CreateStudyGroupCommand(10L, "변경된 이름", 1L, List.of(20L, 30L));

        Long firstGroupId = service.create(first);
        Long retriedGroupId = service.create(retry);

        assertThat(retriedGroupId).isEqualTo(firstGroupId);
        assertThat(studyGroupRepository.count()).isEqualTo(1);
        assertThat(groupMemberRepository.findAllByStudyGroupId(firstGroupId))
                .extracting(member -> member.getUserId())
                .containsExactly(1L, 20L);
    }
}
```

- [ ] **Step 2: 기본 통합 테스트 실행**

Run: `.\gradlew.bat test --tests "com.mycom.myapp.study.service.StudyGroupCreationIntegrationTest"`

Expected: 실제 그룹·그룹원 저장과 순차 재요청 테스트 PASS

- [ ] **Step 3: 그룹원 저장 실패 전체 롤백 테스트 추가**

다음 필드, import와 테스트를 `StudyGroupCreationIntegrationTest`에 추가한다.

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
```

```java
@MockitoSpyBean private GroupMemberRepository groupMemberRepository;
```

기존 `@Autowired private GroupMemberRepository groupMemberRepository;` 필드는 위 필드로
교체한다.

```java
@Test
void rollsBackGroupWhenMemberPersistenceFails() {
    DataIntegrityViolationException failure =
            new DataIntegrityViolationException("member persistence failed");
    doThrow(failure).when(groupMemberRepository).flush();

    try {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateStudyGroupCommand(
                                                10L,
                                                "알고리즘 스터디",
                                                1L,
                                                List.of(20L))))
                .isSameAs(failure);
    } finally {
        reset(groupMemberRepository);
    }

    assertThat(studyGroupRepository.findByPostId(10L)).isEmpty();
}
```

- [ ] **Step 4: 롤백 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.mycom.myapp.study.service.StudyGroupCreationIntegrationTest.rollsBackGroupWhenMemberPersistenceFails"`

Expected: 예외가 전파되고 `study_groups`에 `postId=10`인 행이 남지 않아 PASS

- [ ] **Step 5: 병렬 요청 동시성 테스트 추가**

다음 import와 테스트를 `StudyGroupCreationIntegrationTest`에 추가한다.

```java
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
```

```java
@Test
void concurrentRequestsReturnSameGroupWithoutDuplicates() throws Exception {
    CreateStudyGroupCommand command =
            new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of(20L, 30L));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<Long> creation =
            () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return service.create(command);
            };

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
        Future<Long> first = executor.submit(creation);
        Future<Long> second = executor.submit(creation);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        Long firstGroupId = first.get(10, TimeUnit.SECONDS);
        Long secondGroupId = second.get(10, TimeUnit.SECONDS);

        assertThat(secondGroupId).isEqualTo(firstGroupId);
        assertThat(studyGroupRepository.count()).isEqualTo(1);
        assertThat(groupMemberRepository.findAllByStudyGroupId(firstGroupId))
                .extracting(member -> member.getUserId())
                .containsExactly(1L, 20L, 30L);
    } finally {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
}
```

- [ ] **Step 6: 전체 통합 테스트 반복 실행**

Run: `.\gradlew.bat test --tests "com.mycom.myapp.study.service.StudyGroupCreationIntegrationTest" --rerun-tasks`

Expected: 정상 저장, 순차 멱등성, 롤백과 병렬 요청 테스트 모두 PASS

Run the same command three times.

Expected: 동시성 테스트가 세 번 연속 PASS하며 간헐적 실패가 없음

- [ ] **Step 7: 통합 테스트 커밋**

```powershell
git add src/test/java/com/mycom/myapp/study/service/StudyGroupCreationIntegrationTest.java
git commit -m "test: 그룹 생성 트랜잭션과 동시성 검증"
```

---

### Task 5: 내부 계약 문서화와 최종 검증

**Files:**
- Modify: `docs/part3-group/api.md`
- Modify: `docs/part3-group/context.md`
- Modify: `docs/part3-group/group-creation-plan.md`

**Interfaces:**
- Consumes: Task 1~4에서 검증한 실제 서비스 계약
- Produces: Part2 호출자가 사용할 내부 계약, 완료된 계획 체크리스트와 다음 세션 기준

- [ ] **Step 1: 내부 그룹 생성 계약을 API 문서에 기록**

`docs/part3-group/api.md`를 다음 구조로 갱신한다.

```markdown
# 스터디 그룹 API

## 엔드포인트 목록

그룹 생성은 외부 HTTP 엔드포인트가 아니라 Part2가 호출하는 내부 서비스 계약으로 먼저
구현한다. 그룹 홈과 일정 엔드포인트는 해당 기능 구현 시 추가한다.

## 내부 서비스 계약

### 모집 결과 기반 그룹 생성

- 호출자: Part2 모집·신청 유스케이스
- 진입점: `StudyGroupCreationService.create(CreateStudyGroupCommand)`
- 반환값: 생성되었거나 이미 존재하는 그룹의 `Long` ID

| 입력 | 타입 | 규칙 |
| --- | --- | --- |
| `postId` | `Long` | 필수, 그룹 생성의 멱등 키 |
| `groupName` | `String` | 필수, 양끝 공백 제거 후 빈 값 불가 |
| `leaderUserId` | `Long` | 필수, 최초 `LEADER` |
| `approvedUserIds` | `List<Long>` | 필수 목록, 빈 목록 허용, null 원소 불가 |

- 같은 `postId` 재요청은 최초 그룹과 그룹원을 유지하고 기존 그룹 ID를 반환한다.
- 모집장은 `LEADER`, 중복과 모집장을 제거한 승인 회원은 `MEMBER`로 등록한다.
- 그룹과 초기 그룹원은 하나의 트랜잭션에서 생성한다.
- Part2가 모집글과 사용자 식별자의 유효성을 보장한다.
- Part3는 Part1·Part2 Repository를 직접 조회하지 않는다.
```

- [ ] **Step 2: 컨텍스트 문서를 실제 작업 상태와 동기화**

다음 명령으로 현재 브랜치와 커밋을 확인한다.

```powershell
git branch --show-current
git log -1 --oneline
git status --short
```

`docs/part3-group/context.md`의 마지막 갱신일을 작업일로 바꾸고, 현재 Git 상태에 위 명령의
정확한 출력을 기록한다. 완료된 작업에는 그룹 생성 명령, 트랜잭션 Writer, 멱등 Service와
동시성·롤백 검증을 추가한다. 로드맵 3단계를 완료로 표시하고 바로 다음 작업을 로드맵
4단계인 그룹 조회·그룹 홈 계약 확정으로 변경한다.

- [ ] **Step 3: 계획 체크박스와 구현 대조**

`docs/part3-group/group-creation-plan.md`에서 실제로 완료하고 검증한 단계만 `[x]`로 바꾼다.
다음 시그니처가 운영 코드와 테스트에서 일치하는지 대조한다.

```text
CreateStudyGroupCommand(Long, String, Long, List<Long>)
Long StudyGroupCreationWriter.create(CreateStudyGroupCommand)
Long StudyGroupCreationService.create(CreateStudyGroupCommand)
```

- [ ] **Step 4: Part3 그룹 테스트 실행**

Run: `.\gradlew.bat test --tests "com.mycom.myapp.study.*" --rerun-tasks`

Expected: Entity, Repository, 명령, Writer, Service와 통합 테스트 전체 PASS

- [ ] **Step 5: 전체 테스트 실행**

Run: `.\gradlew.bat test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 포맷 검사 실행**

Run: `.\gradlew.bat spotlessCheck --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 변경 범위 검사**

```powershell
git status --short
git diff --check
git diff --stat origin/develop...HEAD
```

Expected: Part3 `study` 운영·테스트 코드와 `docs/part3-group/**`만 변경되고 공백 오류가 없음

- [ ] **Step 8: 문서 동기화 커밋**

```powershell
git add docs/part3-group/api.md docs/part3-group/context.md docs/part3-group/group-creation-plan.md
git commit -m "docs: 그룹 생성 내부 계약과 작업 상태 반영"
```
