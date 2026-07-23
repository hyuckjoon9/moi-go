# 회원 관리 구현 계획

**목표:** 관리자 회원 검색·상세와 `ACTIVE ↔ SUSPENDED` 전환을 제공한다.

**구조:** 조회는 `admin` read model, 변경은 `member` 관리 포트, 조치 이력은 `admin` 서비스가
조정한다. 변경과 이력은 단일 트랜잭션이다.

## 작업 1 — 상태·소유 도메인 경계

- `MemberStatus`에 `SUSPENDED`를 추가하고 `Member.changeStatus(MemberStatus)`를 만든다.
- `MemberAdministrationPort`와 구현 서비스를 `member`에 추가한다. 대상 조회, ADMIN·WITHDRAWN·자기
  계정 보호, 상태 전이 및 정지 시 `RefreshTokenRepository.deleteByUserId`를 담당한다.
- SQL seed의 `users.status` CHECK 제약과 Part1 상태 문서를 `SUSPENDED`에 맞춘다.
- 실패 테스트: 정지 전환, 해제 전환, ADMIN/WITHDRAWN/자기 계정 거부, 정지 token 폐기.

## 작업 2 — 관리자 회원 조회 read model

- `AdminMemberQueryRepository`와 목록·상세 응답 DTO를 만든다.
- 목록은 keyword(이메일·닉네임 부분 일치), role, status를 조합하고 `created_at DESC, id DESC`로
  정렬해 페이지와 전체 수를 반환한다.
- 상세는 회원 프로필, `group_members`·`study_groups`의 그룹 요약, `admin_audit_logs`의 최근
  10건을 조립한다. 비밀값 컬럼은 조회하지 않는다.
- 실패 테스트: 각 필터 조합, 정렬·페이지, 그룹·이력 포함, 존재하지 않는 회원.

## 작업 3 — 상태 변경 유스케이스와 HTTP API

- `AdminMemberService`가 expectedStatus 충돌·멱등성·관리 포트 호출·상태 변경 이력을 처리한다.
- `AdminMemberController`에 목록, 상세, 상태 변경 API를 추가하고 요청 DTO의 상태 enum 및
  사유(공백 제거 후 5~500자)를 검증한다.
- `ADMIN_MEMBER_OPERATION_NOT_ALLOWED`, `ADMIN_OPERATION_CONFLICT` 오류 코드를 추가한다.
- 실패 테스트: `409` 충돌, 실제 변경의 이력, 멱등 요청 무이력, 포트 실패 시 이력 롤백,
  Controller 응답과 ADMIN 보안 경계.

## 작업 4 — 통합 검증·문서 상태 갱신

- 관련 단위·저장소·Controller·보안 테스트를 먼저 실행하고 전체 `./gradlew test`,
  `./gradlew spotlessCheck`를 실행한다.
- `docs/backoffice`의 설계·API·기능 명세와 구현 결과를 대조하고, 완료 상태와 다음 작업만
  짧게 갱신한다.
