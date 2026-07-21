# Part3 그룹·일정 API

## 공통 계약

- 기본 경로는 `/api`, 인증은 `@AuthenticationPrincipal AuthenticatedMember`이며 Part3는 `id()`만 사용한다.
- 인증 누락은 `UNAUTHORIZED` (`401`), 요청 형식·DTO 검증 실패는 `INVALID_REQUEST` (`400`)다.
- 성공 본문은 `ApiResponse<T>`다. 삭제 성공만 본문 없는 `204 No Content`를 반환한다.
- 모든 `LocalDateTime`은 ISO-8601 형식이다.

| 기능 | 메서드와 경로 | 성공 |
| --- | --- | --- |
| 그룹 홈 | `GET /api/groups/{groupId}` | `200`, `StudyGroupHomeResponse` |
| 일정 생성 | `POST /api/groups/{groupId}/schedules` | `201`, `ScheduleResponse` |
| 일정 목록 | `GET /api/groups/{groupId}/schedules` | `200`, `SchedulePageResponse` |
| 일정 상세 | `GET /api/groups/{groupId}/schedules/{scheduleId}` | `200`, `ScheduleResponse` |
| 일정 수정 | `PUT /api/groups/{groupId}/schedules/{scheduleId}` | `200`, `ScheduleResponse` |
| 응답 마감 변경 | `PATCH /api/groups/{groupId}/schedules/{scheduleId}/response-deadline` | `200`, `ScheduleResponse` |
| 일정 삭제 | `DELETE /api/groups/{groupId}/schedules/{scheduleId}` | `204` |

## 공통 응답 모델

| 모델 | 필드와 타입 |
| --- | --- |
| `StudyGroupHomeResponse` | `Long groupId`, `Long postId`, `String name`, `GroupStatus status`, `LocalDateTime createdAt`, `GroupRole myRole`, `List<GroupMemberSummaryResponse> members` |
| `GroupMemberSummaryResponse` | `Long userId`, `GroupRole role`, `LocalDateTime joinedAt` |
| `ScheduleResponse` | `Long scheduleId/groupId/creatorId`, `String title/location/onlineLink/content/materials`, `LocalDateTime scheduledAt/responseDeadline/createdAt/updatedAt` |
| `ScheduleSummaryResponse` | `Long scheduleId/creatorId`, `String title/location/onlineLink`, `LocalDateTime scheduledAt/responseDeadline` |
| `SchedulePageResponse` | `List<ScheduleSummaryResponse> items`, `int page/size/totalPages`, `long totalElements`, `boolean hasNext` |

`status`는 `ACTIVE` 또는 `ENDED`, 역할은 `LEADER`, `MANAGER`, `MEMBER`다. 선택 문자열과 선택 시각은 값이 없으면 `null`이다.

## 공통 접근·오류

일정 관리 작업의 검증 순서는 그룹 → 그룹원 기록 → 탈퇴 상태 → 그룹 상태 → 관리 역할 → 그룹에 속한 일정 → 작업별 상태·시간이다. 조회는 그룹 상태와 관리 역할을 검사하지 않는다.

| 상황 | 상태 | 오류 코드 | 메시지 |
| --- | --- | --- | --- |
| 인증 없음 | `401` | `UNAUTHORIZED` | 인증이 필요합니다. |
| 잘못된 요청 | `400` | `INVALID_REQUEST` | 잘못된 요청입니다. |
| 그룹 없음 | `404` | `GROUP_NOT_FOUND` | 그룹을 찾을 수 없습니다. |
| 그룹원 기록 없음 | `403` | `GROUP_ACCESS_DENIED` | 그룹에 접근할 권한이 없습니다. |
| 탈퇴 그룹원 | `403` | `WITHDRAWN_GROUP_MEMBER` | 탈퇴한 그룹원은 그룹에 접근할 수 없습니다. |
| 종료 그룹에서 일정 관리 | `409` | `GROUP_ENDED` | 종료된 그룹에서는 일정을 관리할 수 없습니다. |
| 관리 역할 아님 | `403` | `SCHEDULE_MANAGEMENT_FORBIDDEN` | 일정을 관리할 권한이 없습니다. |
| 일정 없음 또는 다른 그룹 소속 | `404` | `SCHEDULE_NOT_FOUND` | 일정을 찾을 수 없습니다. |
| 일정·마감 시간 오류 | `400` | `INVALID_SCHEDULE_TIME` | 일정 또는 응답 마감 시간이 올바르지 않습니다. |
| 시작된 일정 수정 | `409` | `SCHEDULE_UPDATE_NOT_ALLOWED` | 이미 시작된 일정은 수정할 수 없습니다. |
| 일정 삭제 불가 | `409` | `SCHEDULE_DELETE_NOT_ALLOWED` | 출석 또는 활동 이력이 있거나 이미 시작된 일정은 삭제할 수 없습니다. |
| 응답 마감 변경 불가 | `409` | `SCHEDULE_DEADLINE_UPDATE_NOT_ALLOWED` | 마감되었거나 이미 시작된 일정의 응답 마감은 변경할 수 없습니다. |
| 참석 응답 마감 | `409` | `ATTENDANCE_RESPONSE_CLOSED` | 참석 응답 마감 시간이 지났습니다. |

오류 본문은 `ApiResponse.error(message)`를 사용한다. 외부 응답에는 코드 필드가 없으며 표의 코드는 서버 상태·메시지와 테스트 기준이다.

## 내부 그룹 생성 계약

Part2는 `StudyGroupCreationService.create(CreateStudyGroupCommand)`를 호출하고 생성됐거나 기존인 그룹 ID를 받는다.

| 입력 | 규칙 |
| --- | --- |
| `postId` | 필수, 멱등 키 |
| `groupName` | 필수, 양끝 공백 제거 후 빈 값 불가 |
| `leaderUserId` | 필수, 최초 `LEADER` |
| `approvedUserIds` | 필수 목록, 빈 목록 허용, `null` 원소 불가 |

- 같은 `postId` 재요청은 최초 그룹·그룹원을 유지하고 기존 ID를 반환한다.
- 승인 회원은 중복과 모집장을 제거한 뒤 `MEMBER`로 등록한다.
- 그룹과 초기 그룹원은 한 트랜잭션에서 생성한다.
- Part2가 외부 식별자의 유효성을 보장하며 Part3는 Part1·Part2 Repository를 직접 조회하지 않는다.

## 그룹 홈

`GET /api/groups/{groupId}`는 해당 그룹의 활성 그룹원에게 허용한다. 종료 그룹도 조회할 수 있다.

- `members`에는 활성 그룹원만 포함한다.
- 정렬은 `LEADER` → `MANAGER` → `MEMBER`, 같은 역할은 `joinedAt ASC`, `userId ASC`다.
- Part1 소유 닉네임·프로필은 포함하지 않는다.

## 일정 생성

`POST /api/groups/{groupId}/schedules`

| Body 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `title` | 예 | 공백 제거 후 1~100자 |
| `scheduledAt` | 예 | `now`보다 미래 |
| `location` | 아니요 | 공백 제거, 최대 255자, 빈 값은 `null` |
| `onlineLink` | 아니요 | 공백 제거, 최대 500자, 빈 값은 `null`; URL 형식 강제 안 함 |
| `content`, `materials` | 아니요 | 공백 제거, 각각 최대 5,000자, 빈 값은 `null` |
| `responseDeadline` | 아니요 | `now < responseDeadline <= scheduledAt` |

활성 그룹의 활성 `LEADER`·`MANAGER`만 생성한다. `location`과 `onlineLink`가 모두 없어도 된다. 시간 규칙 위반은 `INVALID_SCHEDULE_TIME`이다.

## 일정 조회

### 목록

`GET /api/groups/{groupId}/schedules?scope=upcoming&page=0&size=20`

| Query | 기본값 | 규칙 |
| --- | --- | --- |
| `scope` | `upcoming` | `upcoming` 또는 `past` |
| `page` | `0` | 0 이상 |
| `size` | `20` | 1~100 |

- `upcoming`: `scheduledAt >= now`, `scheduledAt ASC`, `id ASC`
- `past`: `scheduledAt < now`, `scheduledAt DESC`, `id DESC`

### 상세

`GET /api/groups/{groupId}/schedules/{scheduleId}`는 `ScheduleResponse` 전체를 반환한다. 목록과 상세는 역할·그룹 상태와 무관하게 활성 그룹원에게 허용하며, 일정 조회 조건에 `groupId`를 포함한다.

## 일정 수정

`PUT /api/groups/{groupId}/schedules/{scheduleId}`는 `title`, `scheduledAt`, `location`, `onlineLink`, `content`, `materials`를 전체 교체한다. 필드 정규화·길이는 생성과 같다.

- 기존 일정과 새 `scheduledAt`은 모두 `now`보다 미래여야 한다.
- 기존 `responseDeadline`이 있으면 새 `scheduledAt` 이하이어야 한다.
- `creatorId`, `responseDeadline`, `createdAt`은 유지하고 `updatedAt`만 갱신한다.
- 기존 일정이 시작됐으면 `SCHEDULE_UPDATE_NOT_ALLOWED` (`409`)다.

## 응답 마감 변경

`PATCH /api/groups/{groupId}/schedules/{scheduleId}/response-deadline`

```json
{ "responseDeadline": "2026-07-25T18:00:00" }
```

속성은 반드시 포함한다. 명시적 `null`은 제거, 누락은 `INVALID_REQUEST`다.

- 일정과 현재 유효 마감이 모두 `now`보다 미래여야 한다.
- 새 값은 없거나 `now < responseDeadline <= scheduledAt`이어야 한다.
- 마감 변경으로 기존 응답을 삭제하지 않으며 지난 마감을 재개방하지 않는다.
- 시작된 일정이나 현재 마감 이후 변경은 `SCHEDULE_DEADLINE_UPDATE_NOT_ALLOWED` (`409`)다.

유효 마감은 `responseDeadline != null ? responseDeadline : scheduledAt`이다.

## 일정 삭제

`DELETE /api/groups/{groupId}/schedules/{scheduleId}`는 미래 일정만 삭제한다.

- 참석 응답만 있으면 FK `CASCADE`로 함께 삭제한다.
- 출석 또는 활동 기록이 있거나 삭제 시 FK 경합이 발생하면 `SCHEDULE_DELETE_NOT_ALLOWED` (`409`)다.
- 시작됐거나 과거인 일정도 같은 오류로 거부한다.
