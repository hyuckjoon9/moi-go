# Part1 개발 가이드

## 문서 목적

이 문서는 Part1 회원·인증·공통 보안 기반 작업의 기준 문서다. Part1 관련 작업을 시작할 때 이 문서를 먼저 확인하고 브랜치 생성, 변경 범위, 구현 방식, 테스트, 커밋 및 Pull Request 방식을 결정한다.

프로젝트 전체에 공통으로 적용되는 내용은 중복해서 관리하지 않는다.

- 전체 브랜치·커밋·작업 흐름: [`README.md`](../../README.md)
- 목표 패키지 구조와 공통 계층 책임: [`docs/architecture/folder-structure.md`](../architecture/folder-structure.md)
- Part1 API 명세: [`docs/part1-auth/api.md`](api.md)
- Part1 데이터 관계와 제약: [`docs/part1-auth/erd.md`](erd.md)

이 문서는 위 공통 규칙을 Part1에 적용하는 방법과 Part1에만 필요한 경계를 정의한다. 사용자 요청이나 팀 합의가 이 문서와 충돌하면 최신 합의를 우선하고, 작업과 함께 이 문서를 갱신한다.

## 현재 프로젝트 분석 기준

이 가이드는 다음 상태를 기준으로 작성되었다.

- Java 21, Spring Boot 4.1.0, Spring Web, Spring Data JPA, Spring Security, Validation을 사용한다.
- 도메인별 최상위 패키지 아래에 `controller`, `service`, `repository`, `dto`, `entity`를 두는 구조다.
- Git Hook은 커밋 시 `spotlessApply`를 실행하고, CI는 `spotlessCheck`와 전체 테스트를 수행한다.
- 공통 브랜치는 `main`, `develop`, `feature/*`, `fix/*`이며 PR은 `develop`을 대상으로 한다.
- Part1은 `member`, `auth`, `global/security`, `global/config`, `global/exception`, `global/response` 일부를 구현해 다른 파트가 인증 사용자 식별자를 사용할 수 있게 한다.

## Part1 담당 범위

### 담당 도메인

| 도메인 | 소유 경로 | 책임 |
| --- | --- | --- |
| 회원 | `src/main/java/com/mycom/myapp/member/**` | 회원 계정, 프로필, 시스템 권한과 회원 상태 |
| 인증 | `src/main/java/com/mycom/myapp/auth/**` | 로그인, refresh token 저장, token 재발급, 로그아웃 |
| 보안 공통 | `src/main/java/com/mycom/myapp/global/security/**` | 인증 사용자 조회, JWT 발급·검증, 인증 principal |
| 공통 설정 | `src/main/java/com/mycom/myapp/global/config/SecurityConfig.java` | Security filter chain, PasswordEncoder |
| 공통 응답·예외 | `src/main/java/com/mycom/myapp/global/{response,exception}/**` | API 응답 형식, 비즈니스 예외, 오류 코드, 전역 예외 처리 |
| Part1 테스트 | `src/test/java/com/mycom/myapp/{member,auth}/**` | 회원·인증 단위 및 통합 테스트 |
| Part1 문서 | `docs/part1-auth/**` | API와 Part1 전용 설계·결정·개발 규칙 |

### 다른 파트와의 경계

| 영역 | 소유 파트 | Part1의 제공 또는 사용 원칙 |
| --- | --- | --- |
| `recruitment`, `application` | Part2 | 인증된 회원 식별자를 제공한다. 모집글·신청 상태는 Part2가 관리한다. |
| `study`, `schedule` | Part3 | 인증된 회원 식별자를 제공한다. 그룹 역할과 일정 권한은 Part3가 관리한다. |
| `attendance` | Part4 | 인증된 회원 식별자를 제공한다. 출석 응답·체크 권한은 Part4가 관리한다. |
| `activity` | 담당 파트 | 인증된 회원 식별자를 제공한다. 활동 기록·리뷰 규칙은 해당 담당 영역이 관리한다. |
| `global` | 공유 | Part1은 인증 기반에 필요한 최소 공통 기능을 구현하되, 다른 파트의 비즈니스 규칙을 global로 이동하지 않는다. |

다른 파트는 가능한 한 `@AuthenticationPrincipal AuthenticatedMember`로 현재 사용자 식별자를 전달받는다. 다른 파트가 회원 상세 조회가 필요하면 `MemberService`의 공개 메서드 또는 별도 조회 계약을 합의한다.

### Part1에서 수정 가능한 영역

Part1 작업 브랜치에서는 원칙적으로 다음 경로만 수정한다.

```text
src/main/java/com/mycom/myapp/member/**
src/main/java/com/mycom/myapp/auth/**
src/main/java/com/mycom/myapp/global/security/**
src/main/java/com/mycom/myapp/global/config/SecurityConfig.java
src/main/java/com/mycom/myapp/global/exception/**
src/main/java/com/mycom/myapp/global/response/**
src/test/java/com/mycom/myapp/member/**
src/test/java/com/mycom/myapp/auth/**
src/test/resources/**
docs/part1-auth/**
```

아키텍처 문서가 실제 공통 보안 구조와 어긋날 때는 `docs/architecture/folder-structure.md`를 함께 갱신한다.

### 다른 파트 코드 수정 원칙

다른 파트 또는 빌드 설정을 변경해야 할 때는 다음 순서를 따른다.

1. 변경 필요성과 계약을 해당 영역 담당자에게 먼저 공유한다.
2. 기존 공개 경계만으로 해결할 수 있는지 먼저 확인한다.
3. 필요한 최소 범위만 변경하고 담당 파트의 테스트를 함께 보완한다.
4. 독립적으로 검토할 수 있으면 별도 브랜치와 PR로 분리한다.
5. 같은 PR에 포함해야 한다면 PR 설명에 변경 경로, 이유, 영향받는 파트와 검증 결과를 명시하고 담당자의 리뷰를 받는다.

## Part1 데이터 소유권과 관계

Part1은 다음 두 테이블과 대응하는 Entity를 소유한다. 세부 컬럼, 관계, 인덱스와 삭제 정책은 [`erd.md`](erd.md)를 데이터 계약의 기준으로 삼는다.

| 테이블 | 대응 영역 | 핵심 제약과 규칙 |
| --- | --- | --- |
| `users` | `member` | 이메일과 닉네임은 유일하다. 역할은 `USER`, `ADMIN`, 상태는 `ACTIVE`, `WITHDRAWN`만 허용한다. |
| `refresh_tokens` | `auth` | token은 유일하다. `user_id`는 `users.id`를 참조하고 회원 삭제 시 함께 삭제된다. |

### Part1 비즈니스 불변식

- 같은 이메일로 중복 가입할 수 없다.
- 같은 닉네임으로 중복 가입하거나 수정할 수 없다.
- 비밀번호는 평문 저장하지 않고 `PasswordEncoder`로 암호화한다.
- 로그인은 `ACTIVE` 회원만 허용한다.
- refresh token은 DB에 저장된 값만 재발급에 사용할 수 있다.
- 재발급 시 기존 refresh token을 폐기하고 새 refresh token을 저장한다.
- 로그아웃은 전달받은 refresh token을 삭제한다.
- 탈퇴 회원은 인증과 내 정보 접근을 제한한다.

## 브랜치 전략

공통 브랜치 전략은 `README.md`를 따른다. Part1 브랜치는 반드시 작업 이름에 `part1` 또는 인증·회원 목적이 드러나는 이름을 포함하고, `develop`에서 생성한다.

### 네이밍 규칙

```text
feature/part1-<작업을 나타내는 kebab-case 이름>
fix/part1-<수정할 문제를 나타내는 kebab-case 이름>
```

현재 Part1 전체 인증 기반 브랜치는 다음 이름을 사용한다.

```text
feature/part1-auth
```

`main`에 직접 push하지 않는다. 작업 브랜치를 원격에 push한 뒤 GitHub에서 `develop` 대상 Pull Request를 만든다.

## 커밋 전략

커밋 메시지는 `README.md`의 `<type>: <한글 설명>` 형식을 따른다.

| 타입 | 사용 기준 | Part1 예시 |
| --- | --- | --- |
| `feat` | 회원·인증 기능 추가 | `feat: 회원 인증 기능 구현` |
| `fix` | 잘못된 인증, 검증, 예외 처리 수정 | `fix: 만료된 토큰 재발급 검증 수정` |
| `refactor` | 외부 동작을 바꾸지 않는 구조 개선 | `refactor: JWT 생성 로직 분리` |
| `test` | 테스트 추가·수정만 포함 | `test: 회원 중복 검증 테스트 추가` |
| `docs` | 문서만 추가·수정 | `docs: Part1 인증 API 명세 추가` |
| `chore` | 빌드, 설정, 포맷 등 제품 동작과 직접 관련 없는 작업 | `chore: 테스트 JWT 설정 추가` |

커밋 하나에는 리뷰 가능한 하나의 논리적 변경만 담는다. 기능 구현, 무관한 리팩터링, 다른 파트 수정과 문서 정리를 한 커밋에 섞지 않는다.

## 권장 작업 순서

1. 원격 `develop`을 최신 상태로 갱신한다.
2. 작업 종류에 맞는 Part1 브랜치를 `develop`에서 생성한다.
3. 관련 코드와 `docs/part1-auth/` 문서를 읽고 변경 범위와 다른 파트 영향 여부를 확인한다.
4. 테스트를 먼저 작성하거나 최소한 완료 조건을 테스트 사례로 정의한다.
5. Controller에서 Entity까지 필요한 최소 범위를 구현한다.
6. 변경한 API가 있으면 같은 작업에서 `docs/part1-auth/api.md`를 갱신한다.
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

현재 로컬 경로에 한글 `문서`가 포함되어 Gradle test worker classpath가 깨지는 문제가 있을 수 있다. 이 경우 코드 검증을 위해 ASCII 경로인 `C:\tmp\moi-go-test`에 임시 복사해 테스트한다. CI나 한글이 없는 경로에서는 일반 `gradlew test`를 사용한다.

## API 작성 규칙

- API Prefix는 프로젝트 공통 관례에 맞춰 `/api`를 사용한다.
- 인증 리소스는 `/api/auth`, 회원 리소스는 `/api/members`를 사용한다.
- 요청 본문은 request DTO로 받고 `@Valid`로 검증한다.
- Entity를 요청 또는 응답 본문으로 직접 노출하지 않는다.
- 로그인과 재발급은 token response DTO만 반환한다.
- 생성은 `201`, 조회·수정·로그인은 `200`을 사용한다.
- 예외를 Controller에서 반복해서 `try/catch`하지 않고 `GlobalExceptionHandler` 또는 JWT 필터의 인증 실패 응답으로 처리한다.

## Part1 개발 원칙

### Controller

- API 계약을 Java 메서드로 연결하는 얇은 계층으로 유지한다.
- 인증이 필요한 API는 `@AuthenticationPrincipal AuthenticatedMember`로 현재 사용자를 받는다.
- request DTO 검증 오류와 비즈니스 예외의 책임을 분리한다.
- 응답은 response DTO로 변환하고 Entity를 노출하지 않는다.

### Service

- 회원가입, 로그인, 재발급, 로그아웃, 내 정보 조회·수정 유스케이스를 조정한다.
- 상태를 변경하는 유스케이스의 트랜잭션 경계를 관리한다.
- 조회 전용 유스케이스는 읽기 전용 트랜잭션을 사용한다.
- 비밀번호 검증과 token 저장·삭제처럼 여러 repository와 보안 컴포넌트 협력이 필요한 로직을 담당한다.

### Repository

- Part1 Entity의 조회와 저장만 담당한다.
- 이메일, 닉네임, refresh token 조회 조건을 메서드 이름에 드러낸다.
- 권한, 상태 전이, 응답 조립 같은 비즈니스 로직을 넣지 않는다.

### DTO

- 입력은 `dto/request`, 출력은 `dto/response`에 분리한다.
- API 유스케이스별로 필요한 필드만 선언하고 Entity를 DTO로 재사용하지 않는다.
- 형식과 필수값 검증은 request DTO에서 수행하고, 중복·상태 검증은 Service에서 수행한다.

### Entity

- 회원과 refresh token의 상태 및 생성 규칙을 표현한다.
- 공개 setter를 만들지 않고 의미 있는 생성·상태 변경 메서드를 제공한다.
- 데이터베이스 매핑을 위한 기본 생성자의 접근 범위를 최소화한다.
- Controller, API DTO 또는 HTTP 타입에 의존하지 않는다.

### Security

- `JwtTokenProvider`는 token 생성과 claim 검증만 담당한다.
- `JwtAuthenticationFilter`는 요청 헤더에서 access token을 추출하고 SecurityContext에 `AuthenticatedMember`를 설정한다.
- `SecurityConfig`는 stateless 보안, 공개 인증 API, JWT 필터, 비밀번호 인코더를 구성한다.
- 인증 실패 응답은 공통 JSON 형식을 유지한다.

## 테스트 원칙

- 테스트 패키지는 운영 코드의 `member`, `auth` 구조와 대응시킨다.
- Entity 테스트는 필수값, 기본 상태, 상태 변경과 만료 검증을 확인한다.
- Service 테스트는 중복 이메일·닉네임, 비밀번호 암호화, 로그인 실패, token 재발급과 로그아웃을 확인한다.
- Controller 테스트는 경로, HTTP 상태, 요청 검증, 인증 헤더와 응답 계약을 확인한다.
- 다른 파트와의 인증 사용자 전달 계약을 변경하면 해당 경계를 검증하는 테스트와 PR 설명을 함께 보완한다.

## 문서 유지 원칙

- API 계약은 `api.md`, 데이터 관계는 `erd.md`, Part1 작업 규칙은 이 문서에 기록해 역할을 섞지 않는다.
- 전체 아키텍처나 공통 Git 규칙은 원본 문서를 수정하고 이 문서에서는 링크와 Part1 적용 방식만 유지한다.
- Part1 범위, 브랜치 정책, 커밋 규칙, API Prefix 또는 공통 응답 방식이 바뀌면 관련 코드 작업과 같은 PR에서 이 문서를 갱신한다.