# 모집글 관리자 조회·숨김 구현 계획

**목표:** 관리자가 모집글을 제목 또는 모집장 닉네임으로 검색하고 상세를 확인하며, 사유를 남겨 숨김·복구할 수 있게 한다.

**구조:** `admin` 모듈은 JDBC projection으로 목록·상세를 조회하고 공개 관리 포트를 통해서만 노출 상태를 변경한다. `/backoffice/index.html`의 기존 단일 화면에 모집글 목록과 상세 패널을 추가하며, JavaScript는 현재 회원 관리 흐름과 동일한 인증·페이지 상태 모델을 따른다.

**제약:** API는 `/api/admin/**` 보안 정책을 그대로 사용한다. 검색은 제목과 모집장 닉네임의 부분 일치다. 목록은 0-기반 페이지, 기본 20·최대 100개다. 숨김·복구의 사유는 공백 제거 후 5~500자이며 실제 변경 시에만 운영 이력을 저장한다. `superpowers` 폴더는 만들지 않는다.

## 작업 1: 서버 목록·상세 조회 계약

- [ ] `AdminRecruitmentListResponse`를 추가해 목록 항목(ID, 제목, 모집장 ID·닉네임, 카테고리, 모집 상태, 노출 상태, 생성 시각)과 페이지 메타데이터를 표현한다.
- [ ] `AdminRecruitmentQueryRepositoryTest`에 제목 검색, 모집장 닉네임 검색, 상태·노출 상태 필터, ID 내림차순 및 페이지 결과를 검증하는 실패 테스트를 작성하고 실행한다.
- [ ] `AdminRecruitmentQueryRepository`에 `findRecruitments(keyword, status, visibility, page, size)`를 추가한다. `recruitment_posts`와 `users`를 조인하고, `lower(title)` 또는 `lower(nickname)` 부분 일치와 enum 필터를 적용한다.
- [ ] 저장소 테스트를 실행해 통과를 확인한다.

## 작업 2: 숨김·복구 서비스와 HTTP API

- [ ] `AdminRecruitmentVisibilityUpdateRequest`를 추가한다. `expectedVisibility`, `visibility`는 `VISIBLE`·`HIDDEN`만 받고 `reason`은 `@NotBlank`, 5~500자로 검증한다.
- [ ] `AdminRecruitmentServiceTest`에 실제 노출 상태 변경이 포트 호출과 `RECRUITMENT_HIDDEN` 또는 `RECRUITMENT_RESTORED` 이력 생성을 수행하고, 같은 상태 요청은 이력을 남기지 않는 실패 테스트를 작성한다.
- [ ] 서비스에 목록 조회를 추가하고, 노출 상태 변경 후 `AdminAuditLog.create(adminId, action, RECRUITMENT, recruitmentId, title, before, after, reason)`를 저장한다. 예상 상태 불일치는 `ADMIN_OPERATION_CONFLICT`로 처리한다.
- [ ] `AdminRecruitmentControllerTest`를 먼저 작성해 목록·상세·노출 상태 변경의 요청 매핑과 서비스 위임을 검증한다.
- [ ] `/api/admin/recruitments` 컨트롤러를 추가한다. `GET /`, `GET /{recruitmentId}`, `PATCH /{recruitmentId}/visibility`를 제공하고 인증 관리자 ID를 서비스에 전달한다.
- [ ] 단위 테스트를 실행해 통과를 확인한다.

## 작업 3: 관리자 콘솔 모집글 화면

- [ ] `index.html`에 검색어, 모집 상태, 노출 상태 필터, 결과 테이블, 페이지네이션, 상세·조치 패널을 추가한다.
- [ ] `backoffice.js`에 모집글 상태, API 로더, 목록·상세 렌더링, 숨김·복구 사유 입력과 PATCH 요청을 추가하고 모집글 뷰 진입 시 목록을 조회한다.
- [ ] `backoffice.css`에 새 화면이 기존 반응형 표·패널 토큰을 사용하도록 필요한 최소 스타일만 추가하고 `index.html`의 stylesheet 캐시 버전을 갱신한다.
- [ ] 정적 자산 테스트로 API 경로, 필터·상세 패널 식별자, 캐시 버전을 검증한다.

## 작업 4: 문서·회귀 검증

- [ ] `docs/backoffice/api.md`에 모집글 목록·상세·노출 변경 API 계약을 추가한다.
- [ ] `docs/backoffice/context.md`, `feature-spec.md`에 제공 기능을 갱신하고 다음 작업을 그룹·일정·출석·활동 읽기 전용 조회로 변경한다.
- [ ] 대상 관리자 테스트와 전체 `./gradlew test`를 실행한다.
