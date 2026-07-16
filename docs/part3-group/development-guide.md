# Part3 개발 가이드

## 문서 목적

이 문서는 Part3 그룹 기능 작업의 기준 문서다. Part3 관련 작업을 시작할 때 이 문서를 먼저 확인하고 브랜치 생성, 변경 범위, 구현 방식, 테스트, 커밋 및 Pull Request 방식을 결정한다.

프로젝트 전체에 공통으로 적용되는 내용은 중복해서 관리하지 않는다.

- 전체 브랜치·커밋·작업 흐름: [`README.md`](../../README.md)
- 목표 패키지 구조와 공통 계층 책임: [`docs/architecture/folder-structure.md`](../architecture/folder-structure.md)
- Part3 API 명세: [`docs/part3-group/api.md`](api.md)
- Part3 데이터 관계와 제약: [`docs/part3-group/erd.md`](erd.md)

이 문서는 위 공통 규칙을 Part3에 적용하는 방법과 Part3에만 필요한 경계를 정의한다. 사용자 요청이나 팀 합의가 이 문서와 충돌하면 최신 합의를 우선하고, 작업과 함께 이 문서를 갱신한다.

## 현재 프로젝트 분석 기준

이 가이드는 다음 상태를 기준으로 작성되었다.

- Java 21, Spring Boot 4.1.0, Spring Web, Spring Data JPA, Spring Security, Validation을 사용한다.
- 도메인별 최상위 패키지 아래에 `controller`, `service`, `repository`, `dto`, `entity`를 두는 구조다.
- Git Hook은 커밋 시 `spotlessApply`를 실행하고, CI는 `spotlessCheck`와 전체 테스트를 수행한다.
- 공통 브랜치는 `main`, `develop`, `feature/*`, `fix/*`이며 PR은 `develop`을 대상으로 한다.
- 현재 Java 클래스는 폴더 구조만 잡힌 스켈레톤이다. Controller 매핑, 서비스 트랜잭션, JPA 매핑, API 응답 구현은 아직 확정된 기존 스타일이 없다.

따라서 아래 API 및 계층 규칙은 기존 구현을 설명하는 것이 아니라, 현재 구조와 의존성을 바탕으로 Part3에서 일관되게 적용할 기준이다.

## Part3 담당 범위

### 담당 도메인

Part3는 스터디 그룹 내부 기능을 담당한다.

| 도메인 | 소유 경로 | 책임 |
| --- | --- | --- |
| 스터디 그룹 | `src/main/java/com/mycom/myapp/study/**` | 그룹 정보, 그룹 구성원, 그룹 역할과 상태, 그룹 홈 |
| 일정 | `src/main/java/com/mycom/myapp/schedule/**` | 그룹 일정 생성·조회·변경 |
| Part3 테스트 | `src/test/java/com/mycom/myapp/{study,schedule}/**` | 위 도메인의 단위·통합 테스트 |
| Part3 문서 | `docs/part3-group/**` | API와 Part3 전용 설계·결정·개발 규칙 |

### 다른 파트와의 경계

| 영역 | 소유 파트 | Part3의 사용 원칙 |
| --- | --- | --- |
| `auth`, `member` | Part1 | 인증된 회원 식별자와 공개 서비스·인터페이스만 사용한다. 인증 내부 구현을 직접 변경하거나 우회하지 않는다. |
| `recruitment`, `application` | Part2 | 승인된 가입 결과 등 합의된 공개 경계를 통해서만 그룹 생성·가입 흐름과 연동한다. |
| `attendance` | Part4 | 그룹·일정 식별자를 제공하되 출석 판정과 출석률 계산은 Part4에 맡긴다. |
| `activity` | Part3 외부 | 일정 식별자를 제공하되 활동 기록과 리뷰의 생성·조회 규칙은 담당 파트에 맡긴다. |
| `global` | 공유 | 공통 응답, 예외, 보안, 설정은 팀 공용 영역이므로 Part3 단독 소유로 간주하지 않는다. |

Part3는 다른 도메인의 엔티티나 리포지토리를 편의상 직접 수정하거나 내부 구현에 결합하지 않는다. 필요한 데이터와 동작은 담당 파트와 공개 서비스, 조회 메서드 또는 DTO 경계를 합의한다.

### Part3에서 수정 가능한 영역

Part3 작업 브랜치에서는 원칙적으로 다음 경로만 수정한다.

```text
src/main/java/com/mycom/myapp/study/**
src/main/java/com/mycom/myapp/schedule/**
src/test/java/com/mycom/myapp/study/**
src/test/java/com/mycom/myapp/schedule/**
docs/part3-group/**
```

루트 `AGENTS.md`의 Part3 안내를 최신 상태로 유지하기 위한 변경은 허용한다.

### 다른 파트 코드 수정 원칙

다른 파트 또는 `global`, 빌드 설정, 공통 테스트 지원 코드를 변경해야 할 때는 다음 순서를 따른다.

1. 변경 필요성과 계약을 해당 영역 담당자에게 먼저 공유한다.
2. 기존 공개 경계만으로 해결할 수 있는지 먼저 확인한다.
3. 필요한 최소 범위만 변경하고 담당 파트의 테스트를 함께 보완한다.
4. Part3 기능 변경과 독립적으로 검토할 수 있으면 별도 브랜치와 PR로 분리한다.
5. 같은 PR에 포함해야 한다면 PR 설명에 변경 경로, 이유, 영향받는 파트와 검증 결과를 명시하고 담당자의 리뷰를 받는다.

## Part3 데이터 소유권과 관계

Part3는 다음 세 테이블과 대응하는 Entity를 소유한다. 세부 컬럼, 관계, 인덱스와 삭제 정책은 [`erd.md`](erd.md)를 데이터 계약의 기준으로 삼는다.

| 테이블 | 대응 영역 | 핵심 제약과 규칙 |
| --- | --- | --- |
| `study_groups` | `study` | `post_id`는 `recruitment_posts.id`를 참조하며 유일해야 한다. 그룹 상태는 `ACTIVE`, `ENDED`만 허용한다. |
| `group_members` | `study` | `(group_id, user_id)`는 유일하다. 역할은 `LEADER`, `MANAGER`, `MEMBER`, 상태는 `ACTIVE`, `WITHDRAWN`만 허용한다. |
| `study_schedules` | `schedule` | 그룹과 등록자를 참조한다. 참석 응답 마감 시간이 있으면 일정 시간보다 늦을 수 없다. |

### 외부 테이블 경계

- `study_groups.post_id`는 Part2의 `recruitment_posts.id`를 참조한다. 정원 마감 또는 최초 승인 조건은 Part2가 판단하고, Part3는 합의된 호출이나 이벤트를 통해 그룹을 한 번만 생성한다. `post_id` 유니크 제약을 이용해 중복 생성을 막는다.
- `group_members.user_id`와 `study_schedules.creator_id`는 Part1의 `users.id`를 참조한다. Part3는 사용자 계정 정보를 직접 수정하지 않고 식별자와 필요한 공개 조회 결과만 사용한다.
- Part4의 출석 데이터와 Part3 외부의 활동 데이터는 `study_schedules.id`를 참조한다. 일정 삭제가 하위 데이터를 연쇄 삭제할 수 있으므로 삭제 API를 구현할 때 담당 파트와 정책을 먼저 합의한다.

### Part3 비즈니스 불변식

- 모집글 하나에는 스터디 그룹을 하나만 생성한다.
- 동일한 사용자를 같은 그룹에 중복 등록하지 않는다.
- 탈퇴한 그룹원은 행을 삭제하지 않고 `WITHDRAWN` 상태로 관리한다.
- 그룹 역할은 `LEADER`, `MANAGER`, `MEMBER`만 사용한다.
- 일정 등록 권한은 활성 상태의 `LEADER` 또는 `MANAGER`에게만 부여한다.
- `response_deadline`이 있으면 `scheduled_at`보다 같거나 이른 값이어야 한다.
- 그룹 또는 일정 삭제는 외래 키의 `ON DELETE` 동작과 다른 파트 데이터 영향을 확인한 뒤 수행한다.

## 브랜치 전략

공통 브랜치 전략은 `README.md`를 따른다. Part3 브랜치는 반드시 작업 이름에 `part3`를 포함하고, `develop`에서 생성한다.

### 네이밍 규칙

```text
feature/part3-<작업을 나타내는 kebab-case 이름>
fix/part3-<수정할 문제를 나타내는 kebab-case 이름>
```

프로젝트가 현재 정의한 작업 브랜치 prefix는 `feature/*`와 `fix/*`뿐이다. 별도의 `refactor/*`, `test/*`, `docs/*` prefix를 임의로 만들지 않고, 버그 수정 이외 작업은 `feature/*` 안에서 작업 성격을 이름에 표시한다.

| 작업 종류 | 브랜치 규칙 | 예시 |
| --- | --- | --- |
| 기능 개발 | `feature/part3-<feature>` | `feature/part3-group-home` |
| 리팩터링 | `feature/part3-refactor-<target>` | `feature/part3-refactor-group-service` |
| 테스트 | `feature/part3-test-<target>` | `feature/part3-test-schedule-service` |
| 문서 | `feature/part3-docs-<topic>` | `feature/part3-docs-api` |
| 버그 수정 | `fix/part3-<problem>` | `fix/part3-group-update-validation` |

브랜치 하나는 하나의 작업 목적만 담당한다. 예를 들어 일정 생성 기능과 그룹 조회 리팩터링은 별도 브랜치로 분리한다. 작업 도중 별개의 문제를 발견하면 현재 브랜치에 함께 넣지 않고 이슈 또는 후속 브랜치로 분리한다.

## 커밋 전략

커밋 메시지는 `README.md`의 `<type>: <한글 설명>` 형식을 따른다. 현재 Git 이력도 scope 없는 `docs:`, `chore:`, `test:` 형식을 사용하므로 `feat(part3):` 같은 새로운 형식을 임의로 도입하지 않는다.

| 타입 | 사용 기준 | Part3 예시 |
| --- | --- | --- |
| `feat` | 사용자 또는 다른 도메인이 사용할 수 있는 기능 추가 | `feat: 스터디 그룹 홈 조회 기능 추가` |
| `fix` | 잘못된 동작, 검증, 계산 또는 예외 처리 수정 | `fix: 그룹 일정 수정 권한 검증 오류 수정` |
| `refactor` | 외부 동작을 바꾸지 않는 구조 개선 | `refactor: 그룹 서비스 역할 검증 로직 분리` |
| `test` | 테스트 추가·수정만 포함 | `test: 스터디 그룹 가입 상태 테스트 추가` |
| `docs` | 문서만 추가·수정 | `docs: Part3 그룹 API 명세 추가` |
| `chore` | 빌드, 설정, 포맷 등 제품 동작과 직접 관련 없는 작업 | `chore: Part3 테스트 픽스처 설정 추가` |

커밋 하나에는 리뷰 가능한 하나의 논리적 변경만 담는다. 기능 구현, 무관한 리팩터링, 다른 파트 수정과 문서 정리를 한 커밋에 섞지 않는다. 커밋 전에 변경 파일을 확인하고 `application.properties` 같은 개인 환경 설정이나 작업과 무관한 파일을 포함하지 않는다.

## 권장 작업 순서

1. 원격 `develop`을 최신 상태로 갱신한다.
2. 작업 종류에 맞는 Part3 브랜치를 `develop`에서 생성한다.
3. 관련 코드와 `docs/part3-group/` 문서를 읽고 변경 범위와 다른 파트 영향 여부를 확인한다.
4. 테스트를 먼저 작성하거나 최소한 완료 조건을 테스트 사례로 정의한다.
5. Controller에서 Entity까지 필요한 최소 범위를 구현한다.
6. 변경한 API가 있으면 같은 작업에서 `docs/part3-group/api.md`를 갱신한다.
7. 관련 테스트와 전체 테스트를 실행한다.
8. Spotless 검사를 실행하고 변경 파일을 다시 확인한다.
9. 논리적 단위로 커밋하고 원격의 동일한 브랜치로 push한다.
10. `develop`을 대상으로 PR을 만들고 목적, 변경 범위, API 변경, 타 파트 영향, 테스트 결과를 적는다.
11. 리뷰와 CI 통과 후 `develop`에 병합한다.

Windows 검증 명령:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat test
```

Git Hook이 커밋할 때 `spotlessApply`를 실행하더라도, 커밋 전 `spotlessCheck`와 테스트를 직접 실행해 결과를 확인한다.

## API 작성 규칙

### 현재 상태와 Prefix 결정

현재 Controller는 빈 클래스이며 기존 엔드포인트가 없어 프로젝트의 API Prefix 관례를 확인할 수 없다. Part3에서는 웹 경로와 정적 리소스 경로를 구분하고 추후 공통 API 정책을 확장하기 쉽도록 `/api` Prefix를 사용한다.

기본 경로는 복수형 리소스 이름으로 작성한다.

```text
/api/groups
/api/groups/{groupId}/schedules
/api/groups/{groupId}/activities
```

현재 프로젝트에 API 버전 정책이 없으므로 `/api/v1`은 임의로 도입하지 않는다. 버전 정책이 팀 공통으로 결정되면 모든 파트와 함께 변경한다.

### 엔드포인트 규칙

- URI는 동사보다 리소스 중심의 복수 명사를 사용한다.
- Part3가 직접 제공하는 하위 리소스는 일정이며 `/api/groups/{groupId}/schedules`를 사용한다. 활동 경로는 담당 파트와 합의된 API 계약을 따른다.
- 조회는 `GET`, 생성은 `POST`, 전체 교체는 `PUT`, 부분 수정은 `PATCH`, 삭제는 `DELETE`를 사용한다.
- 요청 본문은 request DTO로 받고 `@Valid`로 검증한다.
- Entity를 요청 또는 응답 본문으로 직접 노출하지 않는다.
- API를 추가·변경·삭제하면 같은 브랜치에서 `api.md`의 메서드, 경로, 요청, 응답과 오류 조건을 갱신한다.

### Controller 작성 방식

- `@RestController`를 사용한다.
- 클래스 수준 `@RequestMapping`에 `/api`를 포함한 공통 리소스 경로를 선언한다.
- 의존성은 생성자 주입으로 받으며 필드 주입을 사용하지 않는다.
- Controller는 HTTP 입력 변환, 검증, 인증 사용자 전달, 서비스 호출과 응답 변환만 담당한다.
- 비즈니스 규칙, 트랜잭션 처리, 직접적인 Repository 호출을 Controller에 두지 않는다.
- HTTP 상태 코드는 생성 `201`, 성공 조회·수정 `200`, 본문 없는 삭제 `204`처럼 동작에 맞게 명시한다.
- 예외를 Controller에서 반복해서 `try/catch`하지 않고 공통 예외 처리기로 전달한다.

`ApiResponse`와 `GlobalExceptionHandler`도 현재 빈 스켈레톤이므로 구체적인 제네릭 타입이나 응답 JSON 구조는 아직 확정된 규칙으로 간주하지 않는다. 공통 응답 형식이 구현되면 Part3도 이를 사용하고 `api.md`에 실제 형식을 기록한다.

## Part3 개발 원칙

전체 계층 정의는 아키텍처 문서를 따르고, Part3에서는 다음 기준을 추가로 적용한다.

### Controller

- API 계약을 Java 메서드로 연결하는 얇은 계층으로 유지한다.
- 경로 변수와 인증 사용자로 그룹 접근 범위를 명확히 전달한다.
- request DTO 검증 오류와 비즈니스 예외의 책임을 분리한다.
- 응답은 response DTO로 변환하고 Entity를 노출하지 않는다.

### Service

- 그룹과 일정 유스케이스 및 권한 검증을 조정한다.
- 상태를 변경하는 유스케이스의 트랜잭션 경계를 관리한다.
- 조회 전용 유스케이스는 읽기 전용 트랜잭션을 사용한다.
- 다른 파트와의 협력은 공개 서비스 또는 합의된 인터페이스를 통하며, 다른 파트 Repository를 직접 호출하지 않는다.
- Controller에 HTTP 세부 사항이 새어 나가지 않도록 도메인 결과 또는 response DTO 생성에 필요한 값을 반환한다.

### Repository

- Part3 Entity의 조회와 저장만 담당한다.
- 메서드 이름 또는 명시적 쿼리로 조회 의도를 드러낸다.
- 권한, 상태 전이, 응답 조립 같은 비즈니스 로직을 넣지 않는다.
- 그룹에 종속된 데이터를 조회할 때 그룹 식별자를 조건에 포함해 경계를 보장한다.
- 그룹의 모집글 중복, 그룹원 중복과 일정 조회 조건을 각각 `post_id`, `(group_id, user_id)`, `(group_id, scheduled_at)` 기준으로 명확히 표현한다.

### DTO

- 입력은 `dto/request`, 출력은 `dto/response`에 분리한다.
- API 유스케이스별로 필요한 필드만 선언하고 Entity를 DTO로 재사용하지 않는다.
- 형식과 필수값 검증은 request DTO에서 수행하고, 권한과 상태 검증은 Service에서 수행한다.
- DTO 이름은 동작과 방향이 드러나도록 `<대상><동작>Request`, `<대상><용도>Response` 형식을 사용한다.

### Entity

- 그룹, 구성원과 일정의 상태 및 상태 변경 규칙을 표현한다.
- 외부에서 필드를 임의로 바꾸는 공개 setter를 만들지 않고 의미 있는 상태 변경 메서드를 제공한다.
- 데이터베이스 매핑을 위한 기본 생성자의 접근 범위를 최소화한다.
- Controller, API DTO 또는 HTTP 타입에 의존하지 않는다.
- 여러 Entity의 저장 순서와 트랜잭션 조정은 Service에 맡긴다.

## 테스트 원칙

- 테스트 패키지는 운영 코드의 `study`, `schedule` 구조와 대응시킨다.
- Controller 테스트는 경로, HTTP 상태, 요청 검증과 응답 계약을 확인한다.
- Service 테스트는 그룹 중복 생성, 그룹원 중복, `LEADER`·`MANAGER` 일정 등록 권한, 응답 마감 검증과 정상·실패 유스케이스를 확인한다.
- Repository 테스트는 모집글별 그룹, 그룹별 사용자와 그룹별 일정 조회 조건 및 유니크 제약을 확인한다.
- 버그 수정은 실패를 재현하는 테스트를 먼저 추가한 뒤 수정한다.
- 다른 파트와의 계약을 변경하면 해당 경계를 검증하는 테스트와 PR 설명을 함께 보완한다.

## 문서 유지 원칙

- API 계약은 `api.md`, Part3 작업 규칙은 이 문서에 기록해 역할을 섞지 않는다.
- 전체 아키텍처나 공통 Git 규칙은 원본 문서를 수정하고 이 문서에서는 링크와 Part3 적용 방식만 유지한다.
- Part3 범위, 브랜치 정책, 커밋 규칙, API Prefix 또는 공통 응답 방식이 바뀌면 관련 코드 작업과 같은 PR에서 이 문서를 갱신한다.
- 이후 Part3 관련 작업은 루트 `AGENTS.md`를 통해 이 문서를 우선 참고한다.
