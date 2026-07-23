# 회원 관리 화면 구현 계획

**목표:** 관리자 API를 사용해 검색·상세·상태 변경을 제공하는 회원 관리 화면을 만든다.

**구조:** `members.html`은 화면 구조만, `backoffice-members.js`는 인증·조회·상태 변경만 맡는다.
기존 `moiApi`와 `moiAuth`를 재사용하고 서버 응답은 `textContent`로 렌더링한다.

### Task 1: 화면과 API 흐름

- `src/main/resources/static/backoffice/members.html`에 필터, 목록, 상세, 상태 변경 사유 입력을
  만든다.
- `src/main/resources/static/js/backoffice-members.js`에서 `GET /api/members/me` 인증 확인,
  `GET /api/admin/members`, `GET /api/admin/members/{id}`, 상태 PATCH를 구현한다.
- 검색·필터·페이지 이동은 목록을 다시 요청하고, 항목 선택·변경 성공·409 충돌은 상세를 다시
  읽는다.

### Task 2: 화면 검증과 연결

- `index.html`에 회원 관리 진입 링크를 추가한다.
- 빈 목록, 권한 오류, 네트워크 오류, 상태 변경 중복·충돌을 화면 상태 영역으로 표시한다.
- 정적 JavaScript 구문 확인과 전체 Gradle 테스트·Spotless 검사를 실행한다.
