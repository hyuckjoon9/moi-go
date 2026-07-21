# Part3 그룹·일정 API 명세

> 기능별 권한·화면 처리 기준은 [feature-spec.md](feature-spec.md)를 참고한다.

## 공통

- 기본 경로: `/api`
- 인증: 로그인한 사용자가 호출한다. 인증되지 않으면 `401 UNAUTHORIZED`다.
- 날짜·시간: 모든 `LocalDateTime` 값은 ISO-8601 형식이다. 예: `2026-07-25T19:00:00`
- 성공 응답: 삭제를 제외하고 `ApiResponse<T>` 형식이다.

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

- 실패 응답: 오류 코드는 서버 내부·테스트 기준이며 응답 본문에는 노출하지 않는다.

```json
{
  "success": false,
  "data": null,
  "message": "일정을 찾을 수 없습니다."
}
```

## 빠른 목록

| 기능 | 메서드 | 경로 | 성공 |
| --- | --- | --- | --- |
| 그룹 홈 조회 | `GET` | `/groups/{groupId}` | `200` |
| 일정 생성 | `POST` | `/groups/{groupId}/schedules` | `201` |
| 일정 목록 조회 | `GET` | `/groups/{groupId}/schedules` | `200` |
| 일정 상세 조회 | `GET` | `/groups/{groupId}/schedules/{scheduleId}` | `200` |
| 일정 수정 | `PUT` | `/groups/{groupId}/schedules/{scheduleId}` | `200` |
| 응답 마감 변경 | `PATCH` | `/groups/{groupId}/schedules/{scheduleId}/response-deadline` | `200` |
| 일정 삭제 | `DELETE` | `/groups/{groupId}/schedules/{scheduleId}` | `204` |

## 그룹 홈 조회

`GET /api/groups/{groupId}`

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| Path | `groupId` | number | 예 | 그룹 ID |

### 성공 응답 — 200

```json
{
  "success": true,
  "data": {
    "groupId": 10,
    "postId": 101,
    "name": "모이고 스프링 스터디",
    "status": "ACTIVE",
    "createdAt": "2026-07-01T10:00:00",
    "myRole": "MEMBER",
    "members": [
      { "userId": 1, "role": "LEADER", "joinedAt": "2026-07-01T10:00:00" },
      { "userId": 2, "role": "MEMBER", "joinedAt": "2026-07-02T10:00:00" }
    ]
  },
  "message": null
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `status` | `ACTIVE` \| `ENDED` | 그룹 상태 |
| `myRole` | `LEADER` \| `MANAGER` \| `MEMBER` | 호출자의 활성 그룹원 역할 |
| `members` | array | 활성 그룹원만 포함. 리더, 매니저, 일반 멤버 순으로 정렬 |

## 일정 생성

`POST /api/groups/{groupId}/schedules`

### 요청

| 위치 | 이름 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- | --- |
| Path | `groupId` | number | 예 | 그룹 ID |
| Body | `title` | string | 예 | 공백 제거 후 1~100자 |
| Body | `scheduledAt` | string(date-time) | 예 | 현재 시각보다 미래 |
| Body | `location` | string \| null | 아니요 | 공백 제거 후 최대 255자 |
| Body | `onlineLink` | string \| null | 아니요 | 공백 제거 후 최대 500자 |
| Body | `content` | string \| null | 아니요 | 공백 제거 후 최대 5,000자 |
| Body | `materials` | string \| null | 아니요 | 공백 제거 후 최대 5,000자 |
| Body | `responseDeadline` | string(date-time) \| null | 아니요 | 현재 시각보다 미래이고 `scheduledAt` 이하 |

```json
{
  "title": "1주차 스터디",
  "scheduledAt": "2026-07-25T19:00:00",
  "location": "강남 스터디룸 A",
  "onlineLink": null,
  "content": "Spring Boot 기초",
  "materials": "1장 PDF",
  "responseDeadline": "2026-07-24T18:00:00"
}
```

### 성공 응답 — 201

응답 `data`는 아래 "일정 상세 조회"의 `data`와 같다.

## 일정 목록 조회

`GET /api/groups/{groupId}/schedules?scope=upcoming&page=0&size=20`

### 요청

| 위치 | 이름 | 타입 | 기본값 | 규칙 |
| --- | --- | --- | --- | --- |
| Path | `groupId` | number | - | 그룹 ID |
| Query | `scope` | `upcoming` \| `past` | `upcoming` | 조회 범위 |
| Query | `page` | number | `0` | 0 이상 |
| Query | `size` | number | `20` | 1~100 |

### 성공 응답 — 200

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "scheduleId": 21,
        "creatorId": 1,
        "title": "1주차 스터디",
        "scheduledAt": "2026-07-25T19:00:00",
        "location": "강남 스터디룸 A",
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

- `upcoming`: `scheduledAt`이 현재 시각 이상인 일정, 빠른 일정 순
- `past`: `scheduledAt`이 현재 시각보다 이전인 일정, 최근 일정 순

## 일정 상세 조회

`GET /api/groups/{groupId}/schedules/{scheduleId}`

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| Path | `groupId` | number | 예 | 그룹 ID |
| Path | `scheduleId` | number | 예 | 일정 ID |

### 성공 응답 — 200

```json
{
  "success": true,
  "data": {
    "scheduleId": 21,
    "groupId": 10,
    "creatorId": 1,
    "title": "1주차 스터디",
    "scheduledAt": "2026-07-25T19:00:00",
    "location": "강남 스터디룸 A",
    "onlineLink": null,
    "content": "Spring Boot 기초",
    "materials": "1장 PDF",
    "responseDeadline": "2026-07-24T18:00:00",
    "createdAt": "2026-07-20T09:30:00",
    "updatedAt": "2026-07-20T09:30:00"
  },
  "message": null
}
```

## 일정 수정

`PUT /api/groups/{groupId}/schedules/{scheduleId}`

### 요청

경로 변수는 "일정 상세 조회"와 같다. 본문은 `responseDeadline`을 제외한 "일정 생성"의 모든 필드를 필수·선택 여부까지 동일하게 보낸다.

```json
{
  "title": "1주차 스터디 - 변경",
  "scheduledAt": "2026-07-26T19:00:00",
  "location": "강남 스터디룸 B",
  "onlineLink": null,
  "content": "Spring Boot 기초",
  "materials": "1장 PDF"
}
```

### 성공 응답 — 200

응답 `data`는 "일정 상세 조회"의 `data`와 같다. 기존 `responseDeadline`은 유지된다.

## 응답 마감 변경

`PATCH /api/groups/{groupId}/schedules/{scheduleId}/response-deadline`

### 요청

`responseDeadline` 키는 반드시 포함한다. `null`을 보내면 별도 마감 시각을 제거한다.

```json
{ "responseDeadline": "2026-07-24T18:00:00" }
```

```json
{ "responseDeadline": null }
```

### 성공 응답 — 200

응답 `data`는 "일정 상세 조회"의 `data`와 같다.

## 일정 삭제

`DELETE /api/groups/{groupId}/schedules/{scheduleId}`

### 성공 응답 — 204

응답 본문이 없다.

## 오류 응답

| HTTP | 내부 코드 | 사용자 메시지 | 주요 발생 조건 |
| --- | --- | --- | --- |
| 400 | `INVALID_REQUEST` | 잘못된 요청입니다. | 누락·형식 오류, 잘못된 목록 쿼리 |
| 400 | `INVALID_SCHEDULE_TIME` | 일정 또는 응답 마감 시간이 올바르지 않습니다. | 과거 일정, 잘못된 마감 시각 |
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. | 미인증 |
| 403 | `GROUP_ACCESS_DENIED` | 그룹에 접근할 권한이 없습니다. | 그룹원 기록 없음 |
| 403 | `WITHDRAWN_GROUP_MEMBER` | 탈퇴한 그룹원은 그룹에 접근할 수 없습니다. | 탈퇴 그룹원 |
| 403 | `SCHEDULE_MANAGEMENT_FORBIDDEN` | 일정을 관리할 권한이 없습니다. | 리더·매니저가 아닌 사용자의 관리 요청 |
| 404 | `GROUP_NOT_FOUND` | 그룹을 찾을 수 없습니다. | 존재하지 않는 그룹 |
| 404 | `SCHEDULE_NOT_FOUND` | 일정을 찾을 수 없습니다. | 존재하지 않거나 다른 그룹의 일정 |
| 409 | `GROUP_ENDED` | 종료된 그룹에서는 일정을 관리할 수 없습니다. | 종료 그룹의 생성·수정·마감 변경·삭제 |
| 409 | `SCHEDULE_UPDATE_NOT_ALLOWED` | 이미 시작된 일정은 수정할 수 없습니다. | 시작된 일정 수정 |
| 409 | `SCHEDULE_DEADLINE_UPDATE_NOT_ALLOWED` | 마감되었거나 이미 시작된 일정의 응답 마감은 변경할 수 없습니다. | 시작·마감 이후 마감 변경 |
| 409 | `SCHEDULE_DELETE_NOT_ALLOWED` | 출석 또는 활동 이력이 있거나 이미 시작된 일정은 삭제할 수 없습니다. | 시작된 일정, 출석·활동 이력 존재 |
| 409 | `ATTENDANCE_RESPONSE_CLOSED` | 참석 응답 마감 시간이 지났습니다. | 참석 응답이 실질 마감 이후 |
