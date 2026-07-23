# 회원 관리 설계

## 범위

관리자는 이메일·닉네임, 역할, 상태로 회원을 검색하고 생성일 내림차순 페이지 목록과 상세를
조회한다. 상세는 프로필, 그룹 요약, 대상 회원의 최근 운영 이력을 제공하며 비밀번호·토큰은
절대 포함하지 않는다.

`PATCH /api/admin/members/{memberId}/status`는 일반 회원만 `ACTIVE`와 `SUSPENDED` 사이에서
변경한다. `ADMIN`, `WITHDRAWN`, 요청 관리자 본인은 변경할 수 없다.

## 경계

`admin`의 `JdbcClient` read model은 목록·상세 projection을 만든다. 상태 변경은 `member`의
공개 관리 포트가 처리한다. `AdminMemberService`는 상태 변경, 정지 시 refresh token 폐기,
운영 이력 저장을 하나의 트랜잭션으로 조정한다.

## 상태 전이

- 요청 상태와 현재 상태가 같으면 최신 상세를 반환하고 이력을 남기지 않는다.
- 현재 상태가 `expectedStatus`와 요청 상태 모두와 다르면 `409 ADMIN_OPERATION_CONFLICT`다.
- 상태가 실제로 바뀌면 이력을 남긴다. 정지 시 refresh token을 모두 폐기하며, JWT 필터가
  DB의 최신 상태를 확인하므로 기존 access token도 다음 요청부터 무효다.

## 검증

검색·정렬·페이지·상세 조립, 허용/차단 상태 전이, 정지 token 폐기, 이력·롤백·멱등성,
ADMIN 보안 경계와 HTTP 계약을 자동 테스트한다.
