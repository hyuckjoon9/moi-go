# Back Office 회원 관리 설계

## 범위

관리자는 `/api/admin/members`에서 회원을 이메일·닉네임, 역할, 상태로 검색하고 생성일
내림차순 페이지 목록을 조회한다. `/api/admin/members/{memberId}`는 프로필, 그룹 요약,
최근 운영 이력을 반환한다. 비밀번호와 토큰은 반환하지 않는다.

`PATCH /api/admin/members/{memberId}/status`는 일반 회원의 상태만 `ACTIVE`와
`SUSPENDED` 사이에서 변경한다. `WITHDRAWN`과 `ADMIN` 대상은 변경할 수 없으며, 자기
자신도 변경할 수 없다.

## 경계와 흐름

관리자 목록·상세는 `AdminMemberQueryRepository`의 projection read model이 담당한다.
상태 변경은 `member` 패키지의 공개 관리 포트가 Member 상태를 바꾸고, 정지 시
`RefreshTokenRepository.deleteByUserId`로 refresh token을 폐기한다. `AdminMemberService`는
상태 변경과 `AdminAuditLog` 저장을 하나의 트랜잭션으로 조정한다.

상태가 이미 요청 값이면 최신 상세를 `200`으로 반환하고 이력을 만들지 않는다. 현재 상태가
요청의 `expectedStatus`와 요청 `status` 모두와 다르면 `409 ADMIN_OPERATION_CONFLICT`를
반환한다. 변경 성공 시 JWT 필터의 현재 상태 확인으로 다음 요청부터 접근이 차단된다.

## 응답과 오류

API 계약은 `docs/backoffice/api.md`의 회원 관리 섹션을 따른다. 상세의 그룹 요약은
`groupId`, 이름, 그룹 내 역할, 그룹 상태를 제공하며, 최근 조치는 해당 회원 대상의 최신
10건을 제공한다. `ADMIN` 또는 자기 계정 상태 변경은
`403 ADMIN_MEMBER_OPERATION_NOT_ALLOWED`, 존재하지 않는 회원은 기존 `MEMBER_NOT_FOUND`를
사용한다.

## 검증

- read model: 검색 조건 조합, 생성일 정렬, 페이지 계산, 상세의 그룹·이력 조립
- member 관리 포트: 허용 상태 전이, ADMIN/WITHDRAWN 보호, 정지 시 refresh token 폐기
- admin 서비스: 정상 변경+이력, 멱등 요청 무이력, 충돌·권한 오류, 트랜잭션 롤백
- controller/security: ADMIN만 목록·상세·변경 가능, 요청 검증과 `ApiResponse` 계약
- 회귀: `./gradlew test`, `./gradlew spotlessCheck`
