# Part3 다음 세션 컨텍스트

> 마지막 갱신: 2026-07-20
>
> 이 문서는 Part3 작업의 세션 핸드오프 문서다. 다음 세션을 시작하면 먼저 현재 Git 상태를 확인하고, 이 문서의 기록과 실제 저장소가 다르면 실제 저장소를 기준으로 이 문서를 갱신한다.
> 개인 로컬 기록인 `.local/part3/updates.md`가 있으면 이 문서보다 해당 파일의 최신 작업 기록을 우선한다. `updates.md`는 `.git/info/exclude`로 제외하며 커밋하지 않는다.

## 다음 세션 시작 절차

1. 루트 [`AGENTS.md`](../../AGENTS.md)를 읽는다.
2. [`development-guide.md`](development-guide.md), [`erd.md`](erd.md), [`api.md`](api.md)를 읽는다.
3. `.local/part3/updates.md`가 있으면 최신 작업 기록과 바로 다음 작업을 확인한다.
4. 다음 명령으로 브랜치와 변경 사항을 확인한다.

```powershell
git branch --show-current
git status --short
git log -3 --oneline
```

5. 로컬 업데이트가 있으면 그 기록부터, 없으면 아래의 "바로 다음 작업"부터 이어서 진행한다.

## 프로젝트와 담당 범위

- 프로젝트: Moi-Go Spring Boot 백엔드
- Java: 21
- Spring Boot: 4.1.0
- 빌드: Gradle 9.5.1
- 데이터베이스: MySQL 8.0+
- Part3 담당: 그룹·일정
- Part3 소유 패키지: `study`, `schedule`
- Part3 소유 테이블: `study_groups`, `group_members`, `study_schedules`
- Part3 외부 영역: `auth`, `member`, `recruitment`, `application`, `attendance`, `activity`, `global`

## 현재 Git 상태

이 문서를 작성한 시점의 상태다. 다음 세션에서 반드시 다시 확인한다.

- 현재 브랜치: `feature/part3-group-creation`
- HEAD: `0ed09c7 test: 그룹 생성 트랜잭션과 동시성 검증`
- 현재 변경 사항: 없음 (`git status --short` 출력 없음)
- 그룹 생성 유스케이스 구현 전 기준 상태다. 문서 갱신과 최종 검증은 이 세션에서 이어서 기록한다.

현재 변경 파일:

```text
없음
```

## 완료된 작업

- `docs/architecture/folder-structure.md`에 목표 아키텍처가 문서화되어 있다.
- 각 파트의 `api.md` 최소 스켈레톤이 추가되어 있다.
- `docs/part3-group/development-guide.md`에 Part3 개발 규칙이 작성되어 있다.
- 루트 `AGENTS.md`가 Part3 작업 시 개발 가이드를 읽도록 연결한다.
- `docs/part3-group/erd.md`에 Part3 테이블과 외부 FK 경계가 정리되어 있다.
- Part3 범위에서 `activity`를 제외하고 `study`, `schedule`만 소유하도록 바로잡았다.
- API 기본 Prefix는 `/api`로 결정했고, 공통 버전 정책이 없어 `/api/v1`은 도입하지 않았다.
- `StudyGroup`, `GroupMember`, `GroupMemberStatus`와 대응 Repository를 구현했다.
- 외부 모집글·사용자는 다른 파트 Entity에 결합하지 않고 `Long` 식별자로 매핑했다.
- Part3 내부의 `GroupMember.studyGroup`만 지연 로딩 연관관계로 매핑했다.
- Enum 컬럼은 운영 MySQL 스키마와 동일한 `VARCHAR(20)`으로 고정했다.
- 모집글별 그룹, 그룹·사용자별 그룹원, 사용자·상태별 그룹원 조회와 중복 제약 테스트를 추가했다.
- Spring Boot 4.1의 `@DataJpaTest`를 위해 `spring-boot-starter-data-jpa-test`를 추가했다.
- 독립 코드 리뷰 결과 Critical은 없었고, Important 1건과 Minor 항목을 모두 반영했다.
- 마지막 검증에서 전체 테스트와 `spotlessCheck --rerun-tasks`가 통과했다.
- `application-test.properties`에 MySQL JDBC 드라이버를 명시해 CI의 `test` 프로필이 H2 드라이버를 상속하지 않도록 수정했다.
- CI 수정 커밋 `f483319`을 원격 PR 브랜치에 push했다.
- `CreateStudyGroupCommand`가 그룹 생성 입력을 검증하고 이름을 정규화하며 승인 회원 목록을 방어적으로 복사한다.
- `StudyGroupCreationWriter`가 그룹과 정규화된 초기 그룹원을 하나의 트랜잭션으로 저장한다.
- `StudyGroupCreationService`가 `postId` 기준으로 멱등 생성하고 유니크 충돌 뒤 기존 그룹을 재조회한다.
- 그룹 생성의 정상 저장, 순차 재요청, 전체 롤백과 병렬 요청을 통합 테스트로 검증했다.

## 확정된 데이터 규칙

### `study_groups`

- `post_id`는 `recruitment_posts.id`를 참조하며 UNIQUE다.
- 모집글 하나에는 그룹을 하나만 생성한다.
- 상태는 `ACTIVE`, `ENDED`다.
- 정원 마감 또는 최초 승인 조건은 Part2가 판단하고 Part3에 그룹 생성을 요청한다.

### `group_members`

- `(group_id, user_id)`는 UNIQUE다.
- 역할은 `LEADER`, `MANAGER`, `MEMBER`다.
- 상태는 `ACTIVE`, `WITHDRAWN`이다.
- `LEADER`와 활성 `MANAGER`에게 일정 등록 권한을 부여한다.

### `study_schedules`

- 그룹과 등록자 사용자를 참조한다.
- `response_deadline`은 NULL이거나 `scheduled_at`보다 같거나 빨라야 한다.
- 그룹 삭제 시 일정이 연쇄 삭제되고, 일정 삭제 시 출석·활동 데이터도 연쇄 삭제된다.
- 삭제 API를 구현하기 전에 Part4 및 활동 담당자와 정책 합의가 필요하다.

## 아직 결정하거나 확인해야 할 사항

구현 전에 담당 파트와 합의하거나 API 명세에서 확정해야 한다.

1. Part2가 그룹 생성을 요청하는 방식: 직접 서비스 호출, 애플리케이션 이벤트 또는 API 중 하나를 선택한다.
2. 최초 그룹 생성 시 `LEADER`와 승인된 신청자를 `group_members`에 등록하는 주체와 트랜잭션 경계를 정한다.
3. Part1에서 `@AuthenticationPrincipal AuthenticatedMember`로 인증 사용자 ID를 전달하는 공통 방식을 제공한다.
4. Part1에서 `ApiResponse`, `GlobalExceptionHandler`, `ErrorCode` 공통 응답·오류 형식을 제공한다. Part3 구현 시 이 계약을 사용한다.
5. 그룹 홈 응답에 포함할 데이터 범위를 확정한다.
6. 일정 API의 생성·조회·수정·삭제 권한을 확정한다. 현재 확정된 내용은 등록 권한이 `LEADER` 또는 활성 `MANAGER`라는 점이다.
7. `location`과 `online_link` 중 하나 이상을 필수로 할지 결정한다. DB에는 이를 강제하는 CHECK 제약이 없다.
8. 그룹과 일정의 실제 삭제를 허용할지, 상태 종료 또는 소프트 삭제로 대체할지 결정한다.
9. 그룹원 상태 전용 Enum 이름은 `GroupMemberStatus`로 결정했다.

## Part3 구현 로드맵

"해야 할 작업 순서"는 이 프로젝트에서는 **Part3 구현 로드맵**이라고 부른다. 각 단계는 하나의 목적을 가진 별도 브랜치와 PR로 진행한다.

### 0단계: 현재 문서 브랜치 마무리 (완료)

목표: Part3 개발 기준과 ERD를 팀이 검토할 수 있도록 현재 문서 브랜치를 정리한다.

1. 새 문서 내용을 검토한다.
2. `application.properties`를 제외하고 다음 파일만 stage한다.

```powershell
git add AGENTS.md docs/part3-group/development-guide.md docs/part3-group/erd.md docs/part3-group/context.md
```

3. `git diff --cached`로 커밋 범위를 확인한다.
4. 권장 커밋 메시지로 커밋한다.

```text
docs: Part3 개발 가이드와 ERD 추가
```

5. 현재 브랜치를 push하고 `develop` 대상 PR을 만든다.

```powershell
git push -u origin feature/api-docs-skeleton
```

6. 리뷰와 CI 통과 후 `develop`에 병합한다.

### 1단계: Part3 API와 파트 간 계약 확정 (별도 브랜치 생략)

권장 브랜치: `feature/part3-docs-api`

목표: 코드를 작성하기 전에 Part2·Part1·Part4와 맞닿는 계약과 Part3 API를 확정한다.

API 문서는 기능을 구현할 때 같은 브랜치에서 바로 갱신하기로 했다. 파트 간 계약이 필요한
유스케이스를 시작하기 전 해당 항목만 확인하고 구현·테스트·`api.md`를 함께 진행한다.

- 그룹 생성 요청 방식과 중복 요청 처리 방식을 확정한다.
- 그룹 생성 시 리더·승인 회원 등록 책임을 확정한다.
- 인증 사용자 ID 전달 방식을 확인한다.
- 그룹 홈과 일정 API의 메서드, URI, 요청·응답, 권한, 오류를 `api.md`에 작성한다.
- 그룹·일정 삭제 정책을 관련 파트와 합의한다.
- 결정 결과가 장기적으로 남아야 하면 `decisions.md`를 만들거나 기존 결정 문서에 기록한다.

완료 조건: 구현자가 추가 질문 없이 각 API의 성공·실패 테스트를 작성할 수 있다.

### 2단계: 그룹·그룹원 영속성 기반 (완료)

권장 브랜치: `feature/part3-group-persistence`

목표: `study_groups`, `group_members`의 JPA 매핑과 조회 기반을 만든다.

- `StudyGroup`, `GroupMember`, `GroupStatus`, `GroupRole`을 스키마와 일치하게 구현한다.
- 그룹원 상태 Enum을 추가한다.
- 공개 setter 없이 의미 있는 생성·상태 변경 메서드를 제공한다.
- `StudyGroupRepository`, `GroupMemberRepository`를 Spring Data JPA 기반으로 구현한다.
- 모집글별 그룹, 그룹별 사용자, 사용자별 활성 그룹 조회를 구현한다.
- `@DataJpaTest`로 FK, UNIQUE, Enum, 조회 조건을 검증한다.

완료 조건: 그룹과 그룹원을 스키마 제약에 맞게 저장하고 조회할 수 있다.

### 3단계: 그룹 생성과 그룹원 등록 유스케이스 (완료)

권장 브랜치: `feature/part3-group-creation`

목표: Part2의 모집 결과를 바탕으로 그룹을 한 번만 생성하고 초기 그룹원을 구성한다.

- `post_id` 기준 중복 그룹 생성을 방지한다.
- 모집장에게 `LEADER` 역할을 부여한다.
- 승인된 사용자를 `MEMBER`로 등록한다.
- 재요청, 중복 사용자, 존재하지 않는 사용자와 모집글에 대한 실패 규칙을 구현한다.
- 서비스 단위 테스트와 필요한 통합 테스트를 작성한다.

완료 조건: 같은 모집 결과가 여러 번 전달되어도 그룹과 그룹원이 중복 생성되지 않는다.

### 4단계: 그룹 조회와 그룹 홈 API

권장 브랜치: `feature/part3-group-home`

목표: 사용자가 소속 그룹과 그룹 홈 정보를 조회할 수 있게 한다.

- 그룹 단건 조회와 그룹원 목록 조회를 구현한다.
- 현재 사용자의 활성 그룹원 여부와 접근 권한을 검증한다.
- `StudyGroupHomeResponse`의 실제 필드를 API 계약에 맞게 구현한다.
- Controller, Service, Repository 테스트를 작성한다.
- 구현 결과와 `api.md`를 동기화한다.

완료 조건: 활성 그룹원이 그룹 홈을 조회하고, 비회원·탈퇴 회원은 정의된 오류를 받는다.

### 5단계: 일정 영속성 및 생성 API

권장 브랜치: `feature/part3-schedule-create`

목표: 그룹의 `LEADER` 또는 활성 `MANAGER`가 일정을 등록할 수 있게 한다.

- `StudySchedule`을 스키마와 일치하게 구현한다.
- `StudyScheduleRepository`와 그룹별 일정 조회 기반을 구현한다.
- 일정 생성 요청 DTO의 길이, 필수값과 시간 검증을 구현한다.
- 등록자의 그룹원 상태와 역할을 검증한다.
- `response_deadline <= scheduled_at` 규칙을 검증한다.
- Repository, Service, Controller 테스트를 작성한다.

완료 조건: 권한 있는 사용자만 유효한 일정을 생성할 수 있다.

### 6단계: 일정 조회 API

권장 브랜치: `feature/part3-schedule-query`

목표: 그룹원이 그룹별 일정 목록과 상세 정보를 조회할 수 있게 한다.

- `(group_id, scheduled_at)` 인덱스를 활용하는 조회를 구현한다.
- 과거·예정 일정의 정렬 및 조회 범위를 API 명세에 맞춘다.
- 다른 그룹의 일정 접근을 차단한다.
- 조회 DTO와 계층별 테스트를 작성한다.

완료 조건: 그룹원이 자신의 그룹 일정만 정의된 순서로 조회할 수 있다.

### 7단계: 일정 수정·삭제 API

권장 브랜치: `feature/part3-schedule-management`

목표: 합의된 권한과 삭제 정책에 따라 일정을 변경하고 제거한다.

- 수정 가능 필드와 권한을 구현한다.
- 수정 후에도 응답 마감과 일정 시간 관계를 검증한다.
- 삭제 전 출석·활동 데이터 영향 정책을 적용한다.
- 권한 없음, 존재하지 않는 일정, 다른 그룹 접근과 시간 검증 실패 테스트를 작성한다.

완료 조건: 일정 변경·삭제가 파트 간 데이터 정책을 깨뜨리지 않는다.

### 8단계: 통합 검증과 문서 마무리

권장 브랜치: `feature/part3-integration-test`

목표: Part3 전체 흐름과 파트 간 계약을 검증한다.

- 그룹 생성 → 그룹원 조회 → 일정 생성 → 일정 조회·수정 흐름을 통합 테스트한다.
- MySQL과 동일한 제약을 테스트 환경에서도 검증한다.
- 보안 설정과 인증 사용자 전달을 포함한 API 테스트를 수행한다.
- `api.md`, `erd.md`, `development-guide.md`를 실제 구현과 대조해 갱신한다.
- `spotlessCheck`와 전체 테스트를 실행한다.

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat test
```

완료 조건: 관련 테스트와 전체 CI가 통과하고 문서와 구현이 일치한다.

## 바로 다음 작업

다음 작업은 **4단계 그룹 조회와 그룹 홈 계약 확정**이다.

1. Part2·Part1과 그룹 홈 조회에 필요한 입력과 인증 사용자 식별자 전달 경계를 확정한다.
2. `StudyGroupHomeResponse` 필드와 비회원·탈퇴 회원의 오류 계약을 `api.md`에 기록한다.
3. `feature/part3-group-home` 브랜치에서 조회 서비스·Controller·테스트를 구현한다.

## 다음 세션용 시작 요청 예시

```text
AGENTS.md와 docs/part3-group/context.md를 읽고 현재 Git 상태를 확인해줘.
.local/part3/updates.md가 있으면 그 기록을 우선하고,
현재 브랜치의 '바로 다음 작업'부터 이어서 진행해줘.
```

## 세션 종료 시 갱신 규칙

Part3 작업 세션을 마칠 때마다 이 문서에서 다음 내용을 갱신한다.

- 마지막 갱신일
- 현재 브랜치와 HEAD
- 커밋·push·PR 상태
- 완료된 로드맵 단계
- 새로 확정된 결정
- 남은 blocker와 바로 다음 작업
- 보호해야 할 사용자 변경 파일
