# 스터디 그룹 API

## 엔드포인트 목록

그룹 생성은 외부 HTTP 엔드포인트가 아니라 Part2가 호출하는 내부 서비스 계약으로 먼저
구현한다. 그룹 홈은 인증된 활성 그룹원이 그룹 정보와 활성 그룹원 목록을 함께 조회하는
엔드포인트로 제공한다. 일정 생성은 활성 그룹의 `LEADER` 또는 `MANAGER`에게만 제공하고,
일정 조회는 활성 그룹원에게 제공한다.

| 기능 | 메서드 | 경로 | 인증 |
| --- | --- | --- | --- |
| 그룹 홈 조회 | `GET` | `/api/groups/{groupId}` | 필수 |
| 일정 생성 | `POST` | `/api/groups/{groupId}/schedules` | 필수 |
| 일정 목록 조회 | `GET` | `/api/groups/{groupId}/schedules` | 필수 |
| 일정 상세 조회 | `GET` | `/api/groups/{groupId}/schedules/{scheduleId}` | 필수 |

## 내부 서비스 계약

### 모집 결과 기반 그룹 생성

- 호출자: Part2 모집·신청 유스케이스
- 진입점: `StudyGroupCreationService.create(CreateStudyGroupCommand)`
- 반환값: 생성되었거나 이미 존재하는 그룹의 `Long` ID

| 입력 | 타입 | 규칙 |
| --- | --- | --- |
| `postId` | `Long` | 필수, 그룹 생성의 멱등 키 |
| `groupName` | `String` | 필수, 양끝 공백 제거 후 빈 값 불가 |
| `leaderUserId` | `Long` | 필수, 최초 `LEADER` |
| `approvedUserIds` | `List<Long>` | 필수 목록, 빈 목록 허용, null 원소 불가 |

- 같은 `postId` 재요청은 최초 그룹과 그룹원을 유지하고 기존 그룹 ID를 반환한다.
- 모집장은 `LEADER`, 중복과 모집장을 제거한 승인 회원은 `MEMBER`로 등록한다.
- 그룹과 초기 그룹원은 하나의 트랜잭션에서 생성한다.
- Part2가 모집글과 사용자 식별자의 유효성을 보장한다.
- Part3는 Part1·Part2 Repository를 직접 조회하지 않는다.

## 그룹 홈 조회

### 요청

```http
GET /api/groups/{groupId}
Authorization: Bearer {accessToken}
```

| 입력 | 위치 | 타입 | 규칙 |
| --- | --- | --- | --- |
| `groupId` | Path | `Long` | 필수, 조회할 그룹 식별자 |
| 인증 사용자 | Principal | `AuthenticatedMember` | 필수, Part3는 `id()`만 사용 |

- Controller는 `@AuthenticationPrincipal AuthenticatedMember`로 인증 사용자를 전달받는다.
- 인증 정보가 없거나 토큰이 유효하지 않은 경우에는 Part1의 공통 인증 오류 계약을 따른다.
- Part3는 인증 내부 구현이나 Part1의 회원 Repository를 직접 사용하지 않는다.

### 성공 응답

- HTTP 상태: `200 OK`
- 응답 타입: `ApiResponse<StudyGroupHomeResponse>`

```json
{
  "success": true,
  "data": {
    "groupId": 10,
    "postId": 25,
    "name": "토익 스터디",
    "status": "ACTIVE",
    "createdAt": "2026-07-01T10:00:00",
    "myRole": "MEMBER",
    "members": [
      {
        "userId": 1,
        "role": "LEADER",
        "joinedAt": "2026-07-01T10:00:00"
      },
      {
        "userId": 2,
        "role": "MEMBER",
        "joinedAt": "2026-07-02T14:30:00"
      }
    ]
  },
  "message": null
}
```

#### `StudyGroupHomeResponse`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `groupId` | `Long` | 그룹 식별자 |
| `postId` | `Long` | 그룹이 생성된 모집글 식별자 |
| `name` | `String` | 그룹 이름 |
| `status` | `GroupStatus` | 그룹 상태, `ACTIVE` 또는 `ENDED` |
| `createdAt` | `LocalDateTime` | 그룹 생성 시각 |
| `myRole` | `GroupRole` | 현재 인증 사용자의 그룹 내 역할 |
| `members` | `List<GroupMemberSummaryResponse>` | 활성 그룹원 목록 |

#### `GroupMemberSummaryResponse`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | `Long` | 그룹원의 사용자 식별자 |
| `role` | `GroupRole` | 그룹 내 역할 |
| `joinedAt` | `LocalDateTime` | 그룹 가입 시각 |

- Part1이 소유하는 닉네임과 프로필 정보는 이번 응답에 포함하지 않는다.
- `members`에는 `GroupMemberStatus.ACTIVE`인 그룹원만 포함하고 탈퇴 그룹원은 제외한다.
- 그룹원은 `LEADER`, `MANAGER`, `MEMBER` 순으로 정렬한다.
- 같은 역할은 `joinedAt` 오름차순, 가입 시각도 같으면 `userId` 오름차순으로 정렬한다.

### 접근 규칙

- 현재 인증 사용자에게 해당 그룹의 그룹원 기록이 있어야 한다.
- 현재 인증 사용자의 그룹원 상태가 `ACTIVE`여야 한다.
- 그룹 상태가 `ENDED`여도 활성 그룹원은 그룹 홈을 조회할 수 있다.
- 그룹 상태가 `ENDED`인 경우에도 응답의 `status`에 `ENDED`를 그대로 반환한다.
- 종료 그룹의 생성·수정 등 쓰기 작업 허용 여부는 해당 API 계약에서 별도로 정의한다.

### 오류 응답

오류 본문은 공통 `ApiResponse.error(message)` 형식을 따른다.

| 상황 | HTTP 상태 | 오류 코드 | 메시지 |
| --- | --- | --- | --- |
| 그룹이 존재하지 않음 | `404 Not Found` | `GROUP_NOT_FOUND` | `그룹을 찾을 수 없습니다.` |
| 인증 사용자의 그룹원 기록이 없음 | `403 Forbidden` | `GROUP_ACCESS_DENIED` | `그룹에 접근할 권한이 없습니다.` |
| 인증 사용자가 탈퇴 그룹원임 | `403 Forbidden` | `WITHDRAWN_GROUP_MEMBER` | `탈퇴한 그룹원은 그룹에 접근할 수 없습니다.` |
| 인증 실패 | `401 Unauthorized` | Part1 공통 인증 오류 | Part1 공통 인증 계약을 따름 |

`GROUP_NOT_FOUND`, `GROUP_ACCESS_DENIED`, `WITHDRAWN_GROUP_MEMBER`는 그룹 홈 구현 시
공통 영역 담당자와 합의된 변경으로 `global/exception/ErrorCode.java`에 추가한다. 인증 실패를
위한 Part3 전용 오류 코드는 추가하지 않으며 `SecurityConfig`와 JWT 필터도 이번 기능 범위에서
수정하지 않는다.

## 일정 생성

### 요청

```http
POST /api/groups/{groupId}/schedules
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "title": "3주차 스터디",
  "scheduledAt": "2026-07-25T19:00:00",
  "location": null,
  "onlineLink": null,
  "content": "3장 문제 풀이",
  "materials": "교재와 노트북",
  "responseDeadline": "2026-07-24T18:00:00"
}
```

| 입력 | 위치 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- | --- |
| `groupId` | Path | `Long` | 필수 | 일정을 생성할 그룹 식별자 |
| 인증 사용자 | Principal | `AuthenticatedMember` | 필수 | Part3는 `id()`만 사용 |
| `title` | Body | `String` | 필수 | 양끝 공백 제거 후 1~100자 |
| `scheduledAt` | Body | `LocalDateTime` | 필수 | 생성 시점보다 미래 |
| `location` | Body | `String` | 선택 | 양끝 공백 제거 후 최대 255자, 빈 값은 `null` |
| `onlineLink` | Body | `String` | 선택 | 양끝 공백 제거 후 최대 500자, 빈 값은 `null` |
| `content` | Body | `String` | 선택 | 양끝 공백 제거 후 최대 5,000자, 빈 값은 `null` |
| `materials` | Body | `String` | 선택 | 양끝 공백 제거 후 최대 5,000자, 빈 값은 `null` |
| `responseDeadline` | Body | `LocalDateTime` | 선택 | 현재보다 미래이고 `scheduledAt`보다 같거나 빨라야 함 |

- `location`과 `onlineLink`가 모두 `null`이어도 장소 미정 일정으로 생성할 수 있다.
- `location`과 `onlineLink`를 모두 입력하면 온·오프라인 병행 일정으로 처리한다.
- `onlineLink`는 URL 형식을 강제하지 않는다. Discord 채널명, Zoom 회의 ID 등 온라인 접속
  정보를 최대 500자의 일반 문자열로 저장한다.
- 서버는 `onlineLink`를 HTML로 해석하지 않는다. 클라이언트는 `http://` 또는 `https://`로
  시작하는 값만 링크로 표시하고 나머지는 일반 텍스트로 표시한다.
- `location`과 `onlineLink`가 없는 일정의 장소 확정은 이후 일정 수정 API에서 다룬다.

### 권한과 상태 규칙

- 해당 그룹의 그룹원 기록이 있고 상태가 `ACTIVE`여야 한다.
- 활성 그룹원 중 역할이 `LEADER` 또는 `MANAGER`인 사용자만 일정을 생성할 수 있다.
- 역할이 `MEMBER`인 활성 그룹원은 그룹 조회 권한은 있지만 일정 관리 권한은 없다.
- 상태가 `ENDED`인 그룹에는 새 일정을 생성할 수 없다.
- 인증 내부와 Part1 회원 Repository를 직접 사용하지 않는다.
- 검증 순서는 그룹 존재, 그룹원 기록, 탈퇴 상태, 그룹 상태, 역할, 시간 순서로 고정한다.

### 시간 규칙

- 서비스가 사용하는 현재 시각을 `now`라고 할 때 `scheduledAt > now`여야 한다.
- `responseDeadline`은 `null`이거나 `now < responseDeadline <= scheduledAt`이어야 한다.
- `responseDeadline == scheduledAt`은 허용한다.
- 위 규칙 중 하나라도 만족하지 않으면 `INVALID_SCHEDULE_TIME`으로 처리한다.

### 성공 응답

- HTTP 상태: `201 Created`
- 응답 타입: `ApiResponse<ScheduleResponse>`
- 별도의 조회 요청 없이 생성 화면을 갱신할 수 있도록 저장된 일정 전체를 반환한다.

```json
{
  "success": true,
  "data": {
    "scheduleId": 100,
    "groupId": 10,
    "creatorId": 1,
    "title": "3주차 스터디",
    "scheduledAt": "2026-07-25T19:00:00",
    "location": null,
    "onlineLink": null,
    "content": "3장 문제 풀이",
    "materials": "교재와 노트북",
    "responseDeadline": "2026-07-24T18:00:00",
    "createdAt": "2026-07-20T12:00:00",
    "updatedAt": "2026-07-20T12:00:00"
  },
  "message": null
}
```

#### `ScheduleResponse`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `scheduleId` | `Long` | 일정 식별자 |
| `groupId` | `Long` | 소속 그룹 식별자 |
| `creatorId` | `Long` | 일정을 생성한 사용자 식별자 |
| `title` | `String` | 일정 제목 |
| `scheduledAt` | `LocalDateTime` | 일정 시각 |
| `location` | `String` | 오프라인 장소, 미정이면 `null` |
| `onlineLink` | `String` | 온라인 접속 정보, 미정이면 `null` |
| `content` | `String` | 일정 내용, 없으면 `null` |
| `materials` | `String` | 준비물, 없으면 `null` |
| `responseDeadline` | `LocalDateTime` | 참석 응답 마감 시각, 없으면 `null` |
| `createdAt` | `LocalDateTime` | 생성 시각 |
| `updatedAt` | `LocalDateTime` | 마지막 수정 시각, 최초 생성 시 `createdAt`과 동일 |

### 오류 응답

오류 본문은 공통 `ApiResponse.error(message)` 형식을 따른다. 현재 공통 응답에는 오류 코드
필드가 없으므로 아래 오류 코드는 서버 내부의 상태·메시지 선택과 테스트 기준으로 사용한다.

| 상황 | HTTP 상태 | 오류 코드 | 메시지 |
| --- | --- | --- | --- |
| 그룹이 존재하지 않음 | `404 Not Found` | `GROUP_NOT_FOUND` | `그룹을 찾을 수 없습니다.` |
| 인증 사용자의 그룹원 기록이 없음 | `403 Forbidden` | `GROUP_ACCESS_DENIED` | `그룹에 접근할 권한이 없습니다.` |
| 인증 사용자가 탈퇴 그룹원임 | `403 Forbidden` | `WITHDRAWN_GROUP_MEMBER` | `탈퇴한 그룹원은 그룹에 접근할 수 없습니다.` |
| 그룹이 종료됨 | `409 Conflict` | `GROUP_ENDED` | `종료된 그룹에서는 일정을 생성할 수 없습니다.` |
| 활성 그룹원의 역할이 `MEMBER`임 | `403 Forbidden` | `SCHEDULE_MANAGEMENT_FORBIDDEN` | `일정을 관리할 권한이 없습니다.` |
| 일정 또는 마감 시간이 규칙을 위반함 | `400 Bad Request` | `INVALID_SCHEDULE_TIME` | `일정 또는 응답 마감 시간이 올바르지 않습니다.` |
| 필수값 또는 길이 검증 실패 | `400 Bad Request` | `INVALID_REQUEST` | 공통 요청 검증 메시지를 따름 |
| 인증 실패 | `401 Unauthorized` | Part1 공통 인증 오류 | Part1 공통 인증 계약을 따름 |

`GROUP_ENDED`, `SCHEDULE_MANAGEMENT_FORBIDDEN`, `INVALID_SCHEDULE_TIME`은 일정 생성 구현 시
합의된 공통 변경으로 `global/exception/ErrorCode.java`에 추가한다. `ApiResponse`,
`GlobalExceptionHandler`, `SecurityConfig`와 JWT 필터는 이번 기능 범위에서 수정하지 않는다.

## 일정 조회

### 일정 목록 요청

```http
GET /api/groups/{groupId}/schedules?scope=upcoming&page=0&size=20
Authorization: Bearer {accessToken}
```

| 입력 | 위치 | 기본값 | 규칙 |
| --- | --- | --- | --- |
| `groupId` | Path | 없음 | 조회할 그룹 식별자 |
| 인증 사용자 | Principal | 없음 | `AuthenticatedMember.id()`만 사용 |
| `scope` | Query | `upcoming` | `upcoming` 또는 `past` |
| `page` | Query | `0` | 0 이상 |
| `size` | Query | `20` | 1 이상 100 이하 |

- `scope=upcoming`은 조회 기준 시각과 같거나 이후인 일정을 `scheduledAt`, `scheduleId`
  오름차순으로 반환한다.
- `scope=past`는 조회 기준 시각보다 이전인 일정을 `scheduledAt`, `scheduleId`
  내림차순으로 반환한다.
- 예정·지난 목록을 통해 해당 그룹에서 생성된 모든 일정에 접근할 수 있다.

### 일정 목록 성공 응답

- HTTP 상태: `200 OK`
- 응답 타입: `ApiResponse<SchedulePageResponse>`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "scheduleId": 100,
        "creatorId": 1,
        "title": "3주차 스터디",
        "scheduledAt": "2026-07-25T19:00:00",
        "location": null,
        "onlineLink": null,
        "responseDeadline": "2026-07-24T18:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "message": null
}
```

#### `ScheduleSummaryResponse`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `scheduleId` | `Long` | 일정 식별자 |
| `creatorId` | `Long` | 일정 등록자 식별자 |
| `title` | `String` | 일정 제목 |
| `scheduledAt` | `LocalDateTime` | 일정 시각 |
| `location` | `String` | 오프라인 장소, 미정이면 `null` |
| `onlineLink` | `String` | 온라인 접속 정보, 미정이면 `null` |
| `responseDeadline` | `LocalDateTime` | 참석 응답 마감 시각, 없으면 `null` |

#### `SchedulePageResponse`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `items` | `List<ScheduleSummaryResponse>` | 현재 페이지의 일정 요약 목록 |
| `page` | `int` | 현재 페이지 번호 |
| `size` | `int` | 요청한 페이지 크기 |
| `totalElements` | `long` | 전체 일정 수 |
| `totalPages` | `int` | 전체 페이지 수 |
| `hasNext` | `boolean` | 다음 페이지 존재 여부 |

### 일정 상세 요청과 성공 응답

```http
GET /api/groups/{groupId}/schedules/{scheduleId}
Authorization: Bearer {accessToken}
```

- HTTP 상태: `200 OK`
- 응답 타입: `ApiResponse<ScheduleResponse>`
- 과거·예정 일정 구분 없이 기존 `ScheduleResponse`의 전체 필드를 반환한다.
- URL의 `groupId`에 속한 일정만 조회한다.

### 조회 접근 규칙과 오류 응답

- `ACTIVE` 상태의 그룹원은 역할과 관계없이 목록과 상세를 조회할 수 있다.
- `ENDED` 그룹도 활성 그룹원에게 조회를 허용한다.
- 검증 순서는 그룹 존재, 그룹원 기록, 탈퇴 상태, 상세 조회 시 일정 존재·그룹 소속이다.

| 상황 | HTTP 상태 | 오류 코드 | 메시지 |
| --- | --- | --- | --- |
| 그룹이 존재하지 않음 | `404 Not Found` | `GROUP_NOT_FOUND` | `그룹을 찾을 수 없습니다.` |
| 인증 사용자의 그룹원 기록이 없음 | `403 Forbidden` | `GROUP_ACCESS_DENIED` | `그룹에 접근할 권한이 없습니다.` |
| 인증 사용자가 탈퇴 그룹원임 | `403 Forbidden` | `WITHDRAWN_GROUP_MEMBER` | `탈퇴한 그룹원은 그룹에 접근할 수 없습니다.` |
| 일정이 없거나 다른 그룹 소속 | `404 Not Found` | `SCHEDULE_NOT_FOUND` | `일정을 찾을 수 없습니다.` |
| 지원하지 않는 범위 또는 잘못된 페이지 요청 | `400 Bad Request` | `INVALID_REQUEST` | `잘못된 요청입니다.` |
| 인증 실패 | `401 Unauthorized` | `UNAUTHORIZED` | `인증이 필요합니다.` |
