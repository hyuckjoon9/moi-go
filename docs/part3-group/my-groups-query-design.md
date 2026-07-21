# 내 그룹 목록 조회 설계

## 목적

로그인 사용자가 현재 활동 중인 그룹을 한 번에 조회해 프론트엔드가 운영 그룹과 참여 그룹으로 구분해 표시할 수 있게 한다.

## API 계약

- 메서드·경로: `GET /api/groups/me`
- 인증: `@AuthenticationPrincipal AuthenticatedMember`의 사용자 ID를 사용한다.
- 성공 응답: `ApiResponse<List<MyStudyGroupResponse>>`
- 소속 그룹이 없으면 성공 응답의 `data`를 빈 배열로 반환한다.
- 미인증 요청은 `401 UNAUTHORIZED`로 처리한다.

각 목록 항목은 다음 필드를 제공한다.

| 필드 | 의미 |
| --- | --- |
| `groupId` | 그룹 ID |
| `postId` | 그룹을 만든 모집글 ID |
| `name` | 그룹 이름 |
| `status` | 그룹 상태. 이 API에서는 `ACTIVE`만 반환 |
| `role` | 호출자의 `LEADER`, `MANAGER`, `MEMBER` 역할 |
| `joinedAt` | 호출자가 그룹에 가입한 시각 |

## 조회 및 정렬 규칙

다음 조건을 모두 만족하는 `group_members`와 `study_groups`만 반환한다.

1. `group_members.user_id = 로그인 사용자 ID`
2. `group_members.status = ACTIVE`
3. `study_groups.status = ACTIVE`

결과는 `group_members.joined_at DESC`로 정렬한다. 가입 시각이 같으면 결과를 안정적으로 유지하도록 `group_members.id DESC`를 보조 정렬 기준으로 사용한다.

프론트엔드는 별도 서버 분류 없이 `role`을 기준으로 운영 그룹(`LEADER`, `MANAGER`)과 참여 그룹(`MEMBER`)을 구분한다.

## 구현 구조

- `GroupMemberRepository`가 사용자·그룹원 상태·그룹 상태 조건과 정렬을 담당한다.
- 조회 시 `StudyGroup`을 함께 로딩해 목록 변환 과정의 N+1 쿼리를 방지한다.
- `StudyGroupService`는 조회 결과를 `MyStudyGroupResponse` 목록으로 변환한다.
- `StudyGroupController`는 인증 확인, 사용자 ID 전달, 공통 응답 래핑만 담당한다.
- Part2의 모집글·지원 Entity 또는 Repository를 직접 참조하지 않는다. `postId`와 그룹 이름은 Part3가 이미 보유한 `study_groups`에서 제공한다.

## 테스트 범위

- Repository: 다른 사용자, 탈퇴 그룹원, 종료 그룹 제외 및 `joinedAt DESC`, ID 역순 보조 정렬 검증
- Service: 조회 조건 전달, 모든 응답 필드 변환, 빈 목록 반환 검증
- Controller: `/api/groups/me`, 인증 사용자 전달, 공통 성공 응답, 미인증 `401` 검증
- 문서: `api.md`, `feature-spec.md`, `context.md`를 실제 계약과 일치시킨다.

## 제외 범위

- Part2의 신청 중인 그룹 조회
- 모집글 상세 응답 확장
- 테이블·컬럼·인덱스 추가
- 프론트엔드 화면 구현
