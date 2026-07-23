# Back Office 현재 상태

> 기준: 2026-07-23, PR #58

## 구현 완료

- `/backoffice/index.html`: 관리자 인증 가드와 운영 현황 대시보드
- `/backoffice/members.html`: 회원 검색·상세와 `ACTIVE ↔ SUSPENDED` 상태 변경
- `/api/admin/**`: ADMIN 전용 보안, 운영 이력, 대시보드·회원 API
- 회원 정지 시 refresh token 폐기, JWT의 다음 요청 차단, 상태 충돌 `409` 처리
- 모집글 노출 상태(`VISIBLE`/`HIDDEN`)와 일반 사용자 목록·상세·신규 지원 차단
- 모집글 도메인의 관리자 노출 상태 변경 포트
- Back Office 진입 권한 가드와 관리자용 운영 콘솔 테마(라이트/다크)

## 문서 기준

- 기능 범위: `feature-spec.md`
- HTTP 계약: `api.md`
- 경계·구조: `architecture.md`

## 바로 다음 작업

모집글 관리자 검색·상세와 `VISIBLE ↔ HIDDEN` 조치 API·화면·운영 이력을 구현한다. 이후
그룹·일정·출석·활동 읽기 전용 조회, 운영 이력 검색과 전체 Back Office 회귀를 순서대로 진행한다.
