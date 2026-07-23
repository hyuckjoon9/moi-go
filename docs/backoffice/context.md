# Back Office 현재 상태

> 기준: 2026-07-23, PR #58

## 구현 완료

- `/backoffice/index.html`: 관리자 인증 가드와 운영 현황 대시보드
- `/backoffice/members.html`: 회원 검색·상세와 `ACTIVE ↔ SUSPENDED` 상태 변경
- `/api/admin/**`: ADMIN 전용 보안, 운영 이력, 대시보드·회원 API
- 회원 정지 시 refresh token 폐기, JWT의 다음 요청 차단, 상태 충돌 `409` 처리

## 문서 기준

- 기능 범위: `feature-spec.md`
- HTTP 계약: `api.md`
- 경계·구조: `architecture.md`

## 바로 다음 작업

모집글 검색·상세와 `VISIBLE ↔ HIDDEN`을 구현한다. 이후 그룹·일정·출석·활동 읽기 전용
조회, 운영 이력 검색과 전체 Back Office 회귀를 순서대로 진행한다.
