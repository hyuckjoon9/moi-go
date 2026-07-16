# 그룹·그룹원 영속성 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `study_groups`와 `group_members`를 스키마 제약에 맞게 저장하고 Part3 유스케이스에 필요한 조건으로 조회할 수 있게 한다.

**Architecture:** 외부 도메인의 모집글·사용자는 `Long` 식별자로만 참조하고, Part3가 소유하는 그룹–그룹원 관계만 지연 로딩 JPA 연관관계로 매핑한다. 엔티티는 정적 팩터리와 의미 있는 상태 변경 메서드만 공개하고 Repository는 Spring Data JPA 파생 쿼리로 구성한다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Data JPA, Hibernate, JUnit 5, AssertJ, H2 MySQL mode

## Global Constraints

- 수정 범위는 `src/main/java/com/mycom/myapp/study/**`, 대응 테스트와 `docs/part3-group/**`로 제한한다.
- 외부 `member`, `recruitment` 엔티티와 Repository를 수정하거나 직접 연관 매핑하지 않는다.
- 공개 setter를 만들지 않는다.
- 상태 Enum은 `EnumType.STRING`과 길이 20 컬럼을 사용한다.
- API와 서비스 유스케이스는 이번 계획에 포함하지 않는다.

---

### Task 1: 그룹과 그룹원 도메인 모델

**Files:**
- Modify: `src/main/java/com/mycom/myapp/study/entity/GroupStatus.java`
- Modify: `src/main/java/com/mycom/myapp/study/entity/GroupRole.java`
- Create: `src/main/java/com/mycom/myapp/study/entity/GroupMemberStatus.java`
- Modify: `src/main/java/com/mycom/myapp/study/entity/StudyGroup.java`
- Modify: `src/main/java/com/mycom/myapp/study/entity/GroupMember.java`
- Create: `src/test/java/com/mycom/myapp/study/entity/StudyGroupTest.java`
- Create: `src/test/java/com/mycom/myapp/study/entity/GroupMemberTest.java`

**Interfaces:**
- Produces: `StudyGroup.create(Long postId, String name)`, `StudyGroup.end()`
- Produces: `GroupMember.join(StudyGroup studyGroup, Long userId, GroupRole role)`, `GroupMember.changeRole(GroupRole role)`, `GroupMember.withdraw()`
- Produces: `GroupStatus.ACTIVE`, `GroupStatus.ENDED`, `GroupRole.LEADER`, `GroupRole.MANAGER`, `GroupRole.MEMBER`, `GroupMemberStatus.ACTIVE`, `GroupMemberStatus.WITHDRAWN`

- [x] **Step 1: 그룹 상태 테스트 작성**

```java
@Test
void createInitializesActiveGroup() {
    StudyGroup group = StudyGroup.create(10L, "알고리즘 스터디");

    assertThat(group.getPostId()).isEqualTo(10L);
    assertThat(group.getName()).isEqualTo("알고리즘 스터디");
    assertThat(group.getStatus()).isEqualTo(GroupStatus.ACTIVE);
}

@Test
void endChangesStatus() {
    StudyGroup group = StudyGroup.create(10L, "알고리즘 스터디");

    group.end();

    assertThat(group.getStatus()).isEqualTo(GroupStatus.ENDED);
}

@Test
void createRejectsBlankName() {
    assertThatIllegalArgumentException()
            .isThrownBy(() -> StudyGroup.create(10L, " "));
}
```

- [x] **Step 2: 그룹 상태 테스트 실패 확인**

Run: `./gradlew.bat test --tests "com.mycom.myapp.study.entity.StudyGroupTest"`

Expected: `StudyGroup.create`와 Enum 값이 없어 컴파일 실패

- [x] **Step 3: 그룹 엔티티와 Enum 최소 구현**

```java
public enum GroupStatus {
    ACTIVE,
    ENDED
}

public enum GroupRole {
    LEADER,
    MANAGER,
    MEMBER
}
```

`StudyGroup`에는 `@Entity`, `@Table(name = "study_groups", uniqueConstraints = ...)`, IDENTITY 식별자, `post_id`, 길이 100의 `name`, 문자열 `status`, `created_at` 매핑을 추가한다. `create()`는 null 모집글 식별자와 빈 이름을 거부하고 `@PrePersist`는 생성 시각이 비어 있을 때만 채운다.

- [x] **Step 4: 그룹원 상태 테스트 작성**

```java
@Test
void joinInitializesActiveMember() {
    StudyGroup group = StudyGroup.create(10L, "알고리즘 스터디");

    GroupMember member = GroupMember.join(group, 20L, GroupRole.MEMBER);

    assertThat(member.getStudyGroup()).isSameAs(group);
    assertThat(member.getUserId()).isEqualTo(20L);
    assertThat(member.getRole()).isEqualTo(GroupRole.MEMBER);
    assertThat(member.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
}

@Test
void roleAndMembershipStatusChangeThroughDomainMethods() {
    GroupMember member = GroupMember.join(
            StudyGroup.create(10L, "알고리즘 스터디"), 20L, GroupRole.MEMBER);

    member.changeRole(GroupRole.MANAGER);
    member.withdraw();

    assertThat(member.getRole()).isEqualTo(GroupRole.MANAGER);
    assertThat(member.getStatus()).isEqualTo(GroupMemberStatus.WITHDRAWN);
}
```

- [x] **Step 5: 그룹원 테스트 실패 확인**

Run: `./gradlew.bat test --tests "com.mycom.myapp.study.entity.GroupMemberTest"`

Expected: `GroupMember.join`과 `GroupMemberStatus`가 없어 컴파일 실패

- [x] **Step 6: 그룹원 엔티티와 상태 Enum 최소 구현**

```java
public enum GroupMemberStatus {
    ACTIVE,
    WITHDRAWN
}
```

`GroupMember`에는 `@Entity`, `(group_id, user_id)` 유니크 제약, `(user_id, status)` 인덱스, IDENTITY 식별자, 지연 로딩 `StudyGroup`, `user_id`, 문자열 역할·상태, `joined_at` 매핑을 추가한다. `join()`은 null 인수를 거부하고 기본 상태를 `ACTIVE`로 설정한다.

- [x] **Step 7: 도메인 테스트 통과 확인**

Run: `./gradlew.bat test --tests "com.mycom.myapp.study.entity.*Test"`

Expected: 모든 그룹·그룹원 단위 테스트 PASS

- [x] **Step 8: 도메인 모델 커밋**

```powershell
git add src/main/java/com/mycom/myapp/study/entity src/test/java/com/mycom/myapp/study/entity
git commit -m "feat: 그룹과 그룹원 엔티티 구현"
```

---

### Task 2: 그룹과 그룹원 Repository

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/java/com/mycom/myapp/study/repository/StudyGroupRepository.java`
- Modify: `src/main/java/com/mycom/myapp/study/repository/GroupMemberRepository.java`
- Create: `src/test/java/com/mycom/myapp/study/repository/StudyGroupRepositoryTest.java`
- Create: `src/test/java/com/mycom/myapp/study/repository/GroupMemberRepositoryTest.java`

**Interfaces:**
- Consumes: Task 1의 `StudyGroup`, `GroupMember`, `GroupMemberStatus`
- Produces: `Optional<StudyGroup> findByPostId(Long postId)`, `boolean existsByPostId(Long postId)`
- Produces: `Optional<GroupMember> findByStudyGroupIdAndUserId(Long groupId, Long userId)`, `List<GroupMember> findAllByStudyGroupId(Long groupId)`, `List<GroupMember> findAllByUserIdAndStatus(Long userId, GroupMemberStatus status)`

- [x] **Step 1: 그룹 Repository 실패 테스트 작성**

Spring Boot 4.1에서 `@DataJpaTest`를 제공하도록 `build.gradle`의 테스트 의존성에 `org.springframework.boot:spring-boot-starter-data-jpa-test`를 추가한다.

```java
@DataJpaTest
class StudyGroupRepositoryTest {
    @Autowired StudyGroupRepository repository;

    @Test
    void findsGroupByPostId() {
        StudyGroup saved = repository.saveAndFlush(
                StudyGroup.create(10L, "알고리즘 스터디"));

        assertThat(repository.findByPostId(10L)).contains(saved);
        assertThat(repository.existsByPostId(10L)).isTrue();
    }

    @Test
    void rejectsDuplicatePostId() {
        repository.saveAndFlush(StudyGroup.create(10L, "첫 번째 그룹"));

        assertThatThrownBy(() ->
                        repository.saveAndFlush(StudyGroup.create(10L, "두 번째 그룹")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [x] **Step 2: 그룹 Repository 테스트 실패 확인**

Run: `./gradlew.bat test --tests "com.mycom.myapp.study.repository.StudyGroupRepositoryTest"`

Expected: `StudyGroupRepository`가 `JpaRepository`가 아니어서 컴파일 실패

- [x] **Step 3: 그룹 Repository 최소 구현**

```java
public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {
    Optional<StudyGroup> findByPostId(Long postId);

    boolean existsByPostId(Long postId);
}
```

- [x] **Step 4: 그룹원 Repository 실패 테스트 작성**

```java
@DataJpaTest
class GroupMemberRepositoryTest {
    @Autowired StudyGroupRepository studyGroupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;

    @Test
    void findsMembersWithinGroupBoundary() {
        StudyGroup group = studyGroupRepository.saveAndFlush(
                StudyGroup.create(10L, "알고리즘 스터디"));
        GroupMember member = groupMemberRepository.saveAndFlush(
                GroupMember.join(group, 20L, GroupRole.MEMBER));

        assertThat(groupMemberRepository.findByStudyGroupIdAndUserId(group.getId(), 20L))
                .contains(member);
        assertThat(groupMemberRepository.findAllByStudyGroupId(group.getId()))
                .containsExactly(member);
    }

    @Test
    void findsActiveMembershipsByUser() {
        StudyGroup first = studyGroupRepository.save(
                StudyGroup.create(10L, "첫 번째 그룹"));
        StudyGroup second = studyGroupRepository.save(
                StudyGroup.create(11L, "두 번째 그룹"));
        GroupMember active = groupMemberRepository.save(
                GroupMember.join(first, 20L, GroupRole.MEMBER));
        GroupMember withdrawn = GroupMember.join(second, 20L, GroupRole.MEMBER);
        withdrawn.withdraw();
        groupMemberRepository.saveAndFlush(withdrawn);

        assertThat(groupMemberRepository.findAllByUserIdAndStatus(
                        20L, GroupMemberStatus.ACTIVE))
                .containsExactly(active);
    }

    @Test
    void rejectsDuplicateUserWithinGroup() {
        StudyGroup group = studyGroupRepository.saveAndFlush(
                StudyGroup.create(10L, "알고리즘 스터디"));
        groupMemberRepository.saveAndFlush(
                GroupMember.join(group, 20L, GroupRole.MEMBER));

        assertThatThrownBy(() -> groupMemberRepository.saveAndFlush(
                        GroupMember.join(group, 20L, GroupRole.MANAGER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [x] **Step 5: 그룹원 Repository 테스트 실패 확인**

Run: `./gradlew.bat test --tests "com.mycom.myapp.study.repository.GroupMemberRepositoryTest"`

Expected: `GroupMemberRepository`가 `JpaRepository`가 아니어서 컴파일 실패

- [x] **Step 6: 그룹원 Repository 최소 구현**

```java
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    Optional<GroupMember> findByStudyGroupIdAndUserId(Long groupId, Long userId);

    List<GroupMember> findAllByStudyGroupId(Long groupId);

    List<GroupMember> findAllByUserIdAndStatus(Long userId, GroupMemberStatus status);
}
```

- [x] **Step 7: Repository 테스트 통과 확인**

Run: `./gradlew.bat test --tests "com.mycom.myapp.study.repository.*Test"`

Expected: 모든 그룹·그룹원 Repository 테스트 PASS

- [x] **Step 8: Repository 커밋**

```powershell
git add src/main/java/com/mycom/myapp/study/repository src/test/java/com/mycom/myapp/study/repository
git commit -m "feat: 그룹과 그룹원 조회 Repository 구현"
```

---

### Task 3: 문서 동기화와 전체 검증

**Files:**
- Modify: `docs/part3-group/erd.md`
- Modify: `docs/part3-group/group-persistence-plan.md`

**Interfaces:**
- Consumes: Task 1과 Task 2의 실제 Entity·Repository 구현
- Produces: 구현과 일치하는 Part3 영속성 문서와 검증 기록

- [x] **Step 1: ERD의 Entity 대응 내용 갱신**

`StudyGroup.postId`와 `GroupMember.userId`는 외부 도메인 식별자 값으로, `GroupMember.studyGroup`은 Part3 내부 연관관계로 매핑한다는 내용을 `erd.md`에 기록한다.

- [x] **Step 2: 계획 체크박스와 실제 구현 대조**

완료한 단계의 체크박스를 `[x]`로 바꾸고, 메서드 시그니처가 실제 코드와 일치하는지 확인한다.

- [x] **Step 3: Part3 테스트 실행**

Run: `./gradlew.bat test --tests "com.mycom.myapp.study.*"`

Expected: Part3 그룹 테스트 전체 PASS

- [x] **Step 4: 전체 테스트 실행**

Run: `./gradlew.bat test`

Expected: `BUILD SUCCESSFUL`

- [x] **Step 5: 포맷 검사 실행**

Run: `./gradlew.bat spotlessCheck`

Expected: `BUILD SUCCESSFUL`

- [x] **Step 6: 최종 문서 커밋**

```powershell
git add docs/part3-group
git commit -m "docs: 그룹 영속성 매핑 기준 반영"
```
