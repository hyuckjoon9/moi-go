# Back Office 아키텍처

## 목표

Back Office는 일반 사용자 기능과 URL·권한·응답 모델을 분리한 관리자 전용 모듈이다.
관리자는 회원, 모집글, 그룹, 일정, 출석, 활동을 조회하고 제한된 운영 조치를 수행한다.
모든 상태 변경은 사유와 전후 상태를 운영 이력으로 남긴다.

## 검토한 접근

### 1. 기존 도메인 Controller와 Service에 관리자 분기 추가

- 파일 수가 적고 초기 구현이 빠르다.
- 일반 사용자 권한과 관리자 권한이 같은 서비스에 섞여 조건문이 늘어난다.
- 관리자 응답에 필요한 탈퇴 회원·숨김 글까지 기존 응답 모델에 노출될 위험이 있다.

### 2. `admin` 모듈에서 모든 도메인 Repository를 직접 사용

- 관리자 조회와 조치를 한곳에서 구현하기 쉽다.
- `member`, `recruitment`, `study`, `attendance`, `activity`의 내부 Entity와 Repository에
  강하게 결합한다.
- Part3 개발 가이드의 공개 경계 원칙과 충돌한다.

### 3. 관리자 전용 모듈과 읽기/쓰기 경계 분리

- 조회는 `admin` 모듈의 전용 read model이 DB를 읽고 관리자 DTO로 반환한다.
- 상태 변경은 각 소유 도메인의 공개 관리 포트를 호출한다.
- 일반 사용자 유스케이스와 관리자 유스케이스를 분리하면서 연관 종료 트랜잭션을
  명시적으로 조정할 수 있다.

1차 구현은 3번을 채택한다. 현재 규모에서는 별도 조회 서버나 이벤트 저장소를 도입하지
않고 같은 애플리케이션과 데이터베이스 안에서 경계만 분리한다.

## 화면 진입점

정적 화면은 `/backoffice/index.html`을 유일한 최초 진입점으로 사용한다. 프레임워크를
추가하지 않고 현재 HTML·CSS·JavaScript 구성을 유지한다.

```text
/backoffice/index.html
  -> /api/members/me
       -> 인증 없음: /login.html?returnUrl=/backoffice/index.html
       -> USER: 권한 없음 화면, /api/admin/** 호출 금지
       -> ADMIN: 대시보드 로드
  -> /api/admin/dashboard
```

- `/api/members/me`의 `role`과 `status`는 화면 가드와 메뉴 표시를 위한 값이다.
- 실제 권한은 서버가 `/api/admin/**` 요청마다 `ROLE_ADMIN`으로 검사한다.
- 로그인 화면은 기존 로그인을 재사용하며 관리자 전용 비밀번호나 자동 로그인 기능을
  추가하지 않는다.
- 관리자 계정은 DB 운영 절차로만 생성하며 관리자 생성·승격 HTTP API는 제공하지 않는다.
- 백오피스 JavaScript는 기존 사용자 화면용 `app.js`에서 분리해
  `/js/backoffice.js`에 둔다.

1차 대시보드 이후 화면은 아래 경로를 추가한다.

| 화면 | 경로 |
| --- | --- |
| 운영 현황 | `/backoffice/index.html` |
| 회원 | `/backoffice/members.html` |
| 모집글 | `/backoffice/recruitments.html` |
| 그룹·일정·출석·활동 | `/backoffice/groups.html` |
| 운영 이력 | `/backoffice/audit-logs.html` |

## 서버 모듈

```text
com.mycom.myapp.admin
├── controller
├── dto
│   ├── request
│   └── response
├── entity
├── repository
├── service
└── service.port
```

| 구성 요소 | 책임 |
| --- | --- |
| `Admin*Controller` | `/api/admin/**` 매핑, DTO 검증, 인증 관리자 ID 전달 |
| `Admin*Service` | 관리자 유스케이스와 트랜잭션 조정, 운영 이력 생성 |
| `Admin*QueryRepository` | 관리자 전용 목록·집계 projection 조회 |
| `AdminAuditLog` | 성공한 운영 조치의 불변 이력 |
| 관리 포트 | 각 도메인에 상태 변경을 요청하는 공개 인터페이스 |

관리자 조회 Repository는 Entity를 반환하지 않고 DTO projection만 반환한다. 대시보드처럼
여러 도메인의 집계가 필요한 조회는 `JdbcClient` 기반 SQL read model로 구현한다. 이
예외는 조회에만 적용하며 상태 변경 SQL을 직접 실행하지 않는다.

## 보안

`SecurityConfig`는 규칙 순서를 다음과 같이 구성한다.

```java
requestMatchers("/api/admin/**").hasRole("ADMIN")
requestMatchers("/backoffice/**", "/css/**", "/js/**", "/assets/**").permitAll()
anyRequest().authenticated()
```

- 정적 HTML 접근 허용 여부는 데이터 권한과 무관하다. 관리자 데이터는 API에서 보호한다.
- 인증되지 않은 관리자 API 요청은 `401`, 인증됐지만 `USER`인 요청은 `403`을 반환한다.
- 두 오류 모두 `ApiResponse<Void>` JSON 형식을 사용한다.
- `JwtAuthenticationFilter`는 요청마다 DB의 현재 역할과 상태를 사용하므로
  `SUSPENDED` 전환이 다음 요청부터 즉시 반영된다.

## 데이터 모델

### 회원 상태

`MemberStatus`에 `SUSPENDED`를 추가한다.

| 상태 | 의미 | 인증 |
| --- | --- | --- |
| `ACTIVE` | 정상 회원 | 허용 |
| `SUSPENDED` | 관리자 제재 | 거부 |
| `WITHDRAWN` | 사용자 자진 탈퇴 | 거부 |

Back Office 상태 변경은 역할이 `USER`인 회원에 대해서만 `ACTIVE <-> SUSPENDED`를
허용한다. `ADMIN`과 `WITHDRAWN` 회원은 변경하지 않는다.
회원 정지 시 기존 refresh token을 모두 폐기한다. JWT 인증 필터가 매 요청마다 현재 회원
상태를 확인하므로 기존 access token도 다음 요청부터 인증되지 않는다. 정지 해제 후에는
새로 로그인해야 한다.

### 모집글 노출

`RecruitmentVisibility`를 추가하고 모든 기존·신규 모집글의 기본값을 `VISIBLE`로 둔다.
일반 사용자 목록과 상세 조회는 `VISIBLE`만 반환한다. 관리자는 두 상태를 모두 조회한다.
`HIDDEN` 모집글은 작성자를 포함한 일반 사용자에게 `404`로 응답하고 신규 지원을 차단한다.
기존 지원서와 연결된 그룹·일정·출석·활동 데이터는 유지한다.

### 운영 이력

`admin_audit_logs`는 다음 값을 저장한다.

| 필드 | 형식 |
| --- | --- |
| `id` | BIGINT PK |
| `admin_id` | BIGINT, `users.id` FK |
| `action` | VARCHAR(50) |
| `target_type` | VARCHAR(30) |
| `target_id` | BIGINT |
| `target_label` | VARCHAR(255) |
| `before_snapshot` | TEXT JSON |
| `after_snapshot` | TEXT JSON |
| `reason` | VARCHAR(500) |
| `created_at` | DATETIME |

운영 이력은 대상 데이터의 수명과 독립적으로 조회할 수 있어야 하므로 대상 ID에는 다형 FK를 두지 않는다.
관리자 FK는 `ON DELETE RESTRICT`로 둔다. 생성·조회만 제공하고 수정·삭제 API는 만들지
않는다. `targetLabel`에는 조치 당시 회원 닉네임 또는 모집글 제목만 저장하고 이메일과
게시글 본문은 저장하지 않는다. 1차 구현에서는 보존 기간과 자동 삭제 정책을 정의하지
않는다.

## 상태 변경과 트랜잭션

관리자 조치는 다음 순서로 처리한다.

```text
Controller
  -> 요청 DTO 검증
  -> Admin Service
       -> 대상과 현재 상태 조회
       -> 소유 도메인 관리 포트 호출
       -> 변경 결과 확인
       -> AdminAuditLog 저장
  -> 변경된 관리자 상세 DTO 반환
```

- 조치와 운영 이력 저장은 같은 트랜잭션에서 성공하거나 함께 롤백된다.
- 현재 상태가 요청 상태와 이미 같으면 현재 상세를 `200`으로 반환하고 이력을 추가하지
  않는다.
- 화면에서 확인한 예상 상태와 서버의 현재 상태가 다르고 요청 상태와도 다르면 `409`로
  최신 상세를 다시 읽게 한다.
- 모집글·그룹 강제 종료는 1차 범위에서 구현하지 않는다.

## 오류 처리

| 상황 | HTTP | 코드 |
| --- | --- | --- |
| 인증 없음 | 401 | `UNAUTHORIZED` |
| 관리자 권한 없음 | 403 | `ADMIN_ACCESS_DENIED` |
| 관리자 계정 상태 변경 | 403 | `ADMIN_MEMBER_OPERATION_NOT_ALLOWED` |
| 대상 없음 | 404 | 기존 도메인별 `*_NOT_FOUND` |
| 상태 전이 충돌 | 409 | `ADMIN_OPERATION_CONFLICT` |
| 사유 5자 미만·500자 초과 | 400 | `INVALID_REQUEST` |

프론트엔드는 `401`이면 토큰 재발급을 한 번 시도하고 실패하면 로그인으로 이동한다.
`403`이면 관리자 데이터를 추가로 요청하지 않는다. `409`이면 상세를 다시 읽어 최신 상태를
표시한다.

## 구현 단계

각 단계는 독립적으로 테스트하고 배포할 수 있게 구성한다.

1. 관리자 API 보안, 운영 이력 테이블, 대시보드 조회와 화면 진입
2. 회원 목록·상세·`SUSPENDED` 상태 변경과 refresh token 폐기
3. 모집글 목록·상세·숨김/복구와 일반 사용자 노출·지원 차단
4. 그룹·일정·출석·활동 읽기 전용 조회
5. 운영 이력 검색 화면과 전체 회귀 검증

현재 회원 관리 구현 계획은 [member-management-plan.md](member-management-plan.md)에 정의한다.
후속 단계는 직전 단계의 계약과 테스트가 반영된 뒤 별도 계획으로 작성한다.

## 테스트 기준

- Security 통합 테스트: 미인증 `401`, `USER` `403`, `ADMIN` 성공
- Controller 테스트: 경로, DTO 검증, 인증 관리자 ID 전달, 응답 형식
- Service 테스트: 상태 전이, 자기/관리자 계정 보호, 멱등성, 이력 생성·롤백
- Repository 테스트: 검색 조건, 정렬, 페이지, 집계, 이력 기간 조회
- 도메인 통합 테스트: 모집글과 그룹의 동시 종료·롤백
- 프론트 확인: 로그인 복귀 경로, 권한 없음, 빈 상태, 재시도, 사유 유지
- 전체 검증: `./gradlew test`, `./gradlew spotlessCheck`
