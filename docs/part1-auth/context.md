# Part1 다음 세션 컨텍스트

> 마지막 갱신: 2026-07-20
>
> 이 문서는 Part1 작업의 세션 핸드오프 문서다. 다음 세션을 시작하면 먼저 현재 Git 상태를 확인하고, 이 문서의 기록과 실제 저장소가 다르면 실제 저장소를 기준으로 이 문서를 갱신한다.

## 다음 세션 시작 절차

1. 루트 [`AGENTS.md`](../../AGENTS.md)를 읽는다.
2. [`development-guide.md`](development-guide.md), [`erd.md`](erd.md), [`api.md`](api.md)를 읽는다.
3. 다음 명령으로 브랜치와 변경 사항을 확인한다.

```powershell
git branch --show-current
git status --short
git log -3 --oneline
```

4. 로컬 변경과 이 문서의 기록이 다르면 실제 저장소 상태를 기준으로 판단한다.
5. 아래의 "바로 다음 작업"부터 이어서 진행한다.

## 프로젝트와 담당 범위

- 프로젝트: Moi-Go Spring Boot 백엔드
- Java: 21
- Spring Boot: 4.1.0
- 빌드: Gradle 9.5.1
- 데이터베이스: MySQL 8.0+
- Part1 담당: 회원·인증·공통 보안 기반
- Part1 소유 패키지: `member`, `auth`
- Part1 공통 영향 영역: `global/security`, `global/config/SecurityConfig`, `global/exception`, `global/response`
- Part1 소유 테이블: `users`, `refresh_tokens`
- Part1 외부 영역: `recruitment`, `application`, `study`, `schedule`, `attendance`, `activity`

## 현재 Git 상태

이 문서를 작성한 시점의 상태다. 다음 세션에서 반드시 다시 확인한다.

- 현재 브랜치: `feature/part1-auth`
- 현재 브랜치는 아직 원격에 push하지 않았다.
- Part1 회원·인증 기반 구현과 문서 보강 작업이 진행 중이다.
- `main`에 직접 push하지 않는다. 원칙은 `feature/part1-auth` push 후 `develop` 대상 PR이다.

현재 변경 파일은 다음 범주에 속한다.

```text
src/main/java/com/mycom/myapp/member/**
src/main/java/com/mycom/myapp/auth/**
src/main/java/com/mycom/myapp/global/{config,exception,response,security}/**
src/test/java/com/mycom/myapp/member/**
src/test/java/com/mycom/myapp/auth/**
src/test/resources/application*.properties
docs/part1-auth/**
docs/architecture/folder-structure.md
```

## 완료된 작업

- `users` 테이블에 대응하는 `Member`, `MemberRole`, `MemberStatus`를 구현했다.
- `refresh_tokens` 테이블에 대응하는 `RefreshToken`과 `RefreshTokenRepository`를 구현했다.
- 회원가입, 로그인, token 재발급, 로그아웃 API를 구현했다.
- 내 정보 조회와 내 프로필 수정 API를 구현했다.
- `ApiResponse`, `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`를 구현했다.
- `JwtProperties`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `SecurityConfig`를 구현했다.
- `AuthenticatedMember`를 추가해 다른 파트가 `@AuthenticationPrincipal`로 현재 로그인 회원 식별자를 받을 수 있게 했다.
- Part1 API 문서를 작성했다.
- Part1 ERD와 개발 가이드, 세션 컨텍스트 문서를 추가했다.
- `docs/architecture/folder-structure.md`에 `AuthenticatedMember`를 반영했다.
- Part3 문서의 공통 인증·응답 계약 문구를 현재 Part1 구현 기준으로 동기화했다.
- Part1 엔티티 단위 테스트를 추가했다.
- 테스트용 JWT 설정을 추가했다.
- `spotlessApply`와 `spotlessCheck`를 실행했다.
- 한글 경로 classpath 문제를 피하기 위해 `C:\tmp\moi-go-test`에서 `test --rerun-tasks` 전체 테스트가 통과하는 것을 확인했다.

## 확정된 데이터 규칙

### `users`

- `email`은 UNIQUE이며 로그인 ID로 사용한다.
- `nickname`은 UNIQUE다.
- `password`는 암호화된 문자열만 저장한다.
- `role`은 `USER`, `ADMIN`이다.
- `status`는 `ACTIVE`, `SUSPENDED`, `WITHDRAWN`이다.
- 탈퇴는 우선 `WITHDRAWN` 상태 변경으로 처리한다.

### `refresh_tokens`

- `token`은 UNIQUE다.
- `user_id`는 `users.id`를 참조한다.
- 회원 삭제 시 refresh token은 cascade 삭제된다.
- 현재 구현은 로그인 또는 재발급 시 사용자별 기존 refresh token을 삭제하고 새 refresh token을 저장한다.

## 인증 계약

다른 파트는 인증이 필요한 API에서 다음 방식으로 현재 회원을 받는다.

```java
@AuthenticationPrincipal AuthenticatedMember authenticatedMember
```

- `authenticatedMember.id()`: ERD의 `users.id`
- `authenticatedMember.email()`: 로그인 이메일
- `authenticatedMember.role()`: 시스템 권한, `USER` 또는 `ADMIN`

스터디별 역할은 `group_members.role`이므로 Part1의 `role`과 혼동하지 않는다.

## 아직 결정하거나 확인해야 할 사항

1. refresh token을 사용자당 하나만 허용할지, 기기별 여러 개를 허용할지 팀 합의가 필요하다. 현재 구현은 사용자당 하나다.
2. 로그아웃 요청을 refresh token 본문으로 받을지, 추후 쿠키 기반으로 바꿀지 결정해야 한다.
3. 회원 탈퇴 API를 이번 Part1 범위에 포함할지 후속으로 분리할지 결정해야 한다.
4. 관리자 권한 API가 필요하면 `ADMIN`의 실제 사용처와 접근 규칙을 별도로 정의해야 한다.
5. `interests`를 콤마 문자열로 유지할지 별도 매핑 테이블로 확장할지 MVP 이후 결정한다.
6. JWT secret은 운영 환경에서 반드시 환경 변수 또는 외부 설정으로 교체해야 한다.
7. 한글 경로에서 Gradle test worker classpath가 깨지는 현상은 로컬 환경 이슈다. CI 또는 ASCII 경로에서 검증한다.

## Part1 구현 로드맵

### 0단계: 문서와 브랜치 기준 확인 (완료)

목표: README와 docs를 읽고 Part1 작업을 `feature/part1-auth` 브랜치에서 진행한다.

- README의 브랜치·커밋·PR 규칙을 확인했다.
- `main` 직접 push 금지, `develop` 대상 PR 원칙을 확인했다.
- Part3 문서 구조를 참고해 Part1 문서를 보강했다.

### 1단계: 회원·refresh token 영속성 기반 (완료)

목표: `users`, `refresh_tokens`를 스키마 제약에 맞게 저장하고 조회할 수 있게 한다.

- `Member`, `RefreshToken` JPA 매핑을 구현했다.
- 이메일, 닉네임, token 유니크 조회 repository를 구현했다.
- Entity 단위 테스트를 추가했다.

### 2단계: 회원가입과 내 정보 API (완료)

목표: 회원 생성과 내 정보 조회·수정을 제공한다.

- 회원가입 요청 검증과 중복 검증을 구현했다.
- 비밀번호 암호화를 구현했다.
- 내 정보 조회와 프로필 수정 API를 구현했다.

### 3단계: 로그인·JWT·refresh token API (완료)

목표: JWT 기반 stateless 인증을 제공한다.

- 로그인과 access/refresh token 발급을 구현했다.
- refresh token 저장, 재발급, 로그아웃 삭제를 구현했다.
- JWT 필터와 SecurityConfig를 구현했다.
- `AuthenticatedMember` 인증 principal을 제공했다.

### 4단계: 테스트 보강과 PR 준비 (진행 중)

목표: 서비스·컨트롤러 테스트를 보강하고 리뷰 가능한 PR로 정리한다.

- Entity 테스트는 추가되었다.
- Service 테스트와 Controller 테스트는 후속 보강이 필요하다.
- 문서와 아키텍처 동기화가 진행 중이다.
- 최종 `spotlessCheck`와 전체 테스트를 실행한 뒤 커밋한다.

## 바로 다음 작업

1. Service와 Controller 테스트를 추가할지 범위를 결정한다.
2. 변경 파일을 확인하고 논리적 커밋으로 정리한다.
3. 원격 `feature/part1-auth`에 push한다.
4. GitHub에서 `develop` 대상 PR을 만든다.
5. PR 설명에 다음 내용을 포함한다.
   - Part1 구현 범위
   - 공통 영역 변경 이유
   - 다른 파트가 쓰는 인증 사용자 계약
   - API 변경 요약
   - 테스트 결과

## 다음 세션용 시작 요청 예시

```text
AGENTS.md와 docs/part1-auth/context.md를 읽고 현재 Git 상태를 확인해줘.
현재 브랜치의 '바로 다음 작업'부터 이어서 진행해줘.
```

## 세션 종료 시 갱신 규칙

Part1 작업 세션을 마칠 때마다 이 문서에서 다음 내용을 갱신한다.

- 마지막 갱신일
- 현재 브랜치와 HEAD
- 커밋·push·PR 상태
- 완료된 로드맵 단계
- 새로 확정된 결정
- 남은 blocker와 바로 다음 작업
- 보호해야 할 사용자 변경 파일
