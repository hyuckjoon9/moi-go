# Back Office Foundation Design

## Goal

일반 사용자 기능과 분리된 관리자 전용 Back Office의 첫 배포 단위를 구현한다. 이 단위는
관리자 API 보안 경계, 불변 운영 이력 저장소, 운영 현황 조회 API, 관리자 대시보드 진입을
포함한다. 회원 제재와 모집글 숨김 등 상태 변경 기능은 후속 단위로 분리한다.

## Chosen approach

`admin` 전용 모듈을 두고 읽기와 쓰기의 경계를 분리한다.

- 관리자 조회는 `admin`의 DTO projection 및 `JdbcClient` read model이 담당하며 기존
  도메인 Entity를 HTTP 응답으로 반환하지 않는다.
- 미래의 상태 변경은 소유 도메인의 공개 관리 포트를 호출하고, 같은 트랜잭션에서
  `admin_audit_logs`에 변경 이력을 남긴다. 이번 단위는 이 이력의 불변 Entity와 저장소를
  먼저 제공한다.
- `/api/admin/**`는 Spring Security에서 `ADMIN` 역할만 허용한다. 정적 Back Office 화면은
  공개적으로 내려줄 수 있지만, `/api/members/me` 결과가 활성 `ADMIN`일 때만 관리자
  데이터를 요청한다.

기존 도메인 Controller와 Service에 관리자 분기를 추가하는 방식은 사용자 API와 권한 규칙을
섞게 되므로 채택하지 않는다. `admin` 모듈이 기존 도메인 Repository를 직접 사용하는 방식도
도메인 내부 구현에 결합되므로 채택하지 않는다.

## Components and data flow

1. `SecurityConfig`는 `/api/admin/**` 규칙을 다른 permit 규칙보다 먼저 등록한다.
   미인증 요청은 JSON `401`, 일반 회원 요청은 JSON `403`을 `ApiResponse` 형식으로 받는다.
2. `AdminAuditLog`는 관리자 ID, 조치, 대상, 전후 JSON 스냅샷, 사유, 생성 시각을 저장한다.
   생성 팩터리에서 사유를 trim하고 5~500자로 검증하며, 생성 뒤 수정 API나 setter를 제공하지
   않는다.
3. `AdminDashboardQueryRepository`는 `JdbcClient`로 회원·모집글·그룹 수와 최근 운영 조치
   10건을 하나의 `AdminDashboardResponse`로 조립한다.
4. `AdminDashboardService`는 read-only 트랜잭션에서 query repository를 위임하고,
   `AdminDashboardController`는 `GET /api/admin/dashboard` 결과를 `ApiResponse`로 감싼다.
5. `/backoffice/index.html`은 인증 상태를 확인한다. 비로그인은 기존 로그인 화면으로
   `returnUrl`과 함께 이동하고, `USER`는 권한 없음 메시지만 보며 관리자 API를 호출하지
   않는다. 활성 `ADMIN`만 대시보드를 한 번 요청해 안전한 DOM API로 렌더링한다.

## Data model

`admin_audit_logs`는 `users`의 관리자 ID만 외래 키로 참조한다. 대상은 여러 도메인에 걸칠 수
있으므로 다형 외래 키를 사용하지 않는다. 대상 표시는 조치 당시의 닉네임 또는 제목만
저장하고 이메일과 본문은 저장하지 않는다. 최근 조치 조회를 위해
`(created_at DESC, id DESC)` 인덱스를 둔다.

## Errors and security

- 비로그인 `/api/admin/**`: `401 UNAUTHORIZED`, 메시지 `인증이 필요합니다.`
- 일반 회원 `/api/admin/**`: `403 ADMIN_ACCESS_DENIED`, 메시지 `관리자 권한이 필요합니다.`
- 화면에서 발생한 `401`은 로그인으로 이동하고, `403`은 추가 관리자 요청 없이 권한 없음
  상태로 렌더링한다.
- API 클라이언트는 HTTP 상태와 서버 메시지를 JavaScript `Error` 객체에 보존한다.

## Verification

- MockMvc 통합 테스트로 anonymous `401`, `USER` `403`, `ADMIN` `200`을 확인한다.
- 운영 이력 Entity의 사유 정규화·검증과 repository의 최근순 정렬을 확인한다.
- dashboard query repository의 집계와 서비스·Controller의 응답 계약을 확인한다.
- 전체 `./gradlew test` 및 `./gradlew spotlessCheck`를 통과시킨다.
- 수동으로 비로그인, USER, ADMIN 흐름과 대시보드 단일 요청을 확인한다.

## Explicitly deferred

회원 목록·상세·정지, 모집글 목록·상세·숨김, 그룹·일정·출석·활동 조회, 운영 이력 검색 UI는
후속 계획에서 다룬다.
