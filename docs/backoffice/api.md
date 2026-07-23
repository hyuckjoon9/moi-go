# Back Office API 명세 초안

> 이 문서는 팀 협의를 위한 미확정 API 제안이다. 현재 구현된 API를 설명하는 문서가 아니다.
> 다른 프로젝트 문서에서 이 문서를 참조하지 않으며, 폐기할 때는 `docs/backoffice/` 폴더만 삭제한다.
> 업무 규칙과 협의 항목은 [feature-spec.md](feature-spec.md)를 참고한다.

## 공통 규칙

- 기본 경로: `/api/admin`
- 인증: JWT access token을 사용하는 `ADMIN` 전용 API
- 권한 판정: 요청마다 서버에 저장된 현재 회원 역할과 상태를 기준으로 판정한다.
- 날짜·시간: ISO-8601 형식의 `LocalDateTime`. 예: `2026-07-23T14:30:00`
- 페이지 번호는 0부터 시작한다.
- 기본 페이지 크기는 20이며, 최대 크기는 100으로 제한한다.
- 목록 정렬 기본값은 `createdAt,desc`이고 동일 시각에는 `id,desc`를 적용한다.
- 성공과 실패 모두 기존 `ApiResponse<T>` 형식을 따른다.

### 성공 응답

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

### 실패 응답

```json
{
  "success": false,
  "data": null,
  "message": "관리자 권한이 필요합니다."
}
```

### 공통 오류

| HTTP 상태 | 오류 기준 | 설명 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 필드 형식, 페이지 조건 또는 변경 사유가 올바르지 않음 |
| `401` | `UNAUTHORIZED` | access token이 없거나 유효하지 않음 |
| `403` | `ADMIN_ACCESS_DENIED` | 인증됐지만 `ADMIN`이 아님 |
| `404` | 도메인별 `*_NOT_FOUND` | 대상이 존재하지 않음 |
| `409` | `ADMIN_OPERATION_CONFLICT` | 현재 상태에서 요청한 운영 조치를 수행할 수 없음 |

`ADMIN_ACCESS_DENIED`, `ADMIN_OPERATION_CONFLICT`는 현재 `ErrorCode`에 없는 제안 값이다.

## API 빠른 목록

| 기능 | 메서드 | 경로 |
| --- | --- | --- |
| 운영 현황 조회 | `GET` | `/dashboard` |
| 회원 목록 조회 | `GET` | `/members` |
| 회원 상세 조회 | `GET` | `/members/{memberId}` |
| 회원 상태 변경 | `PATCH` | `/members/{memberId}/status` |
| 모집글 목록 조회 | `GET` | `/recruitments` |
| 모집글 상세 조회 | `GET` | `/recruitments/{recruitmentId}` |
| 모집글 숨김 | `PATCH` | `/recruitments/{recruitmentId}/visibility` |
| 모집글 운영 종료 | `POST` | `/recruitments/{recruitmentId}/end` |
| 그룹 목록 조회 | `GET` | `/groups` |
| 그룹 상세 조회 | `GET` | `/groups/{groupId}` |
| 그룹 일정 목록 조회 | `GET` | `/groups/{groupId}/schedules` |
| 그룹 운영 종료 | `POST` | `/groups/{groupId}/end` |
| 일정 출석 요약 조회 | `GET` | `/schedules/{scheduleId}/attendance-summary` |
| 활동 기록 조회 | `GET` | `/schedules/{scheduleId}/activity` |
| 활동 리뷰 목록 조회 | `GET` | `/activity-records/{activityRecordId}/reviews` |
| 운영 이력 목록 조회 | `GET` | `/audit-logs` |
| 운영 이력 상세 조회 | `GET` | `/audit-logs/{auditLogId}` |

## 운영 현황

### 운영 현황 조회

`GET /api/admin/dashboard`

#### 성공 — 200

```json
{
  "success": true,
  "data": {
    "members": {
      "total": 120,
      "active": 116,
      "withdrawn": 4
    },
    "recruitments": {
      "recruiting": 15,
      "closed": 8,
      "active": 21,
      "ended": 34
    },
    "groups": {
      "active": 36,
      "ended": 42
    },
    "recentActions": [
      {
        "auditLogId": 91,
        "action": "RECRUITMENT_HIDDEN",
        "targetType": "RECRUITMENT",
        "targetId": 37,
        "adminId": 1,
        "reason": "운영 정책 위반 확인",
        "createdAt": "2026-07-23T14:30:00"
      }
    ]
  },
  "message": null
}
```

## 회원 관리

### 회원 목록 조회

`GET /api/admin/members?keyword=kim&role=USER&status=ACTIVE&page=0&size=20`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `keyword` | string | 아니요 | 이메일 또는 닉네임 부분 일치 검색 |
| `role` | `USER` \| `ADMIN` | 아니요 | 시스템 역할 필터 |
| `status` | `ACTIVE` \| `WITHDRAWN` | 아니요 | 회원 상태 필터. `SUSPENDED` 채택 시 값 추가 |
| `page` | number | 아니요 | 기본값 `0` |
| `size` | number | 아니요 | 기본값 `20`, 최대 `100` |

#### 성공 — 200

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "memberId": 12,
        "email": "kim@example.com",
        "nickname": "김스터디",
        "role": "USER",
        "status": "ACTIVE",
        "createdAt": "2026-07-20T09:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "message": null
}
```

### 회원 상세 조회

`GET /api/admin/members/{memberId}`

#### 성공 — 200

```json
{
  "success": true,
  "data": {
    "memberId": 12,
    "email": "kim@example.com",
    "nickname": "김스터디",
    "bio": "함께 공부합니다.",
    "interests": "Java,Spring",
    "profileImageUrl": "/uploads/profiles/12.png",
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-07-20T09:00:00",
    "updatedAt": "2026-07-22T11:00:00"
  },
  "message": null
}
```

비밀번호와 토큰은 응답하지 않는다.

### 회원 상태 변경

`PATCH /api/admin/members/{memberId}/status`

```json
{
  "status": "WITHDRAWN",
  "reason": "운영 정책 위반에 따른 이용 제한"
}
```

| Body | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `status` | `ACTIVE` \| `WITHDRAWN` | 예 | `SUSPENDED` 채택 시 값 추가 |
| `reason` | string | 예 | 공백 제외 10~500자 제안 |

#### 성공 — 200

응답 `data`는 회원 상세 조회와 같다.

#### 추가 오류

| HTTP 상태 | 오류 기준 | 상황 |
| --- | --- | --- |
| `403` | `ADMIN_SELF_OPERATION_NOT_ALLOWED` | 운영자가 자기 상태를 변경함 |
| `409` | `LAST_ACTIVE_ADMIN_REQUIRED` | 마지막 활성 관리자를 비활성화하려 함 |

두 오류 기준은 관리자 상태 변경을 허용할 경우에만 필요하다.

## 모집글 관리

### 모집글 목록 조회

`GET /api/admin/recruitments?keyword=스프링&leaderId=12&status=RECRUITING&visibility=VISIBLE&page=0&size=20`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `keyword` | string | 아니요 | 제목 부분 일치 검색 |
| `leaderId` | number | 아니요 | 작성자 회원 ID |
| `category` | string | 아니요 | 카테고리 필터 |
| `status` | `RECRUITING` \| `CLOSED` \| `ACTIVE` \| `ENDED` | 아니요 | 모집 상태 |
| `visibility` | `VISIBLE` \| `HIDDEN` | 아니요 | 제안된 노출 상태 |
| `page` | number | 아니요 | 기본값 `0` |
| `size` | number | 아니요 | 기본값 `20`, 최대 `100` |

#### 성공 — 200

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "recruitmentId": 37,
        "leaderId": 12,
        "title": "스프링 스터디원 모집",
        "category": "개발",
        "status": "RECRUITING",
        "visibility": "VISIBLE",
        "groupId": 18,
        "createdAt": "2026-07-21T10:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "message": null
}
```

### 모집글 상세 조회

`GET /api/admin/recruitments/{recruitmentId}`

성공 응답에는 기존 모집글 상세 필드 전체와 `visibility`, `groupId`를 추가한다.

```json
{
  "success": true,
  "data": {
    "recruitmentId": 37,
    "leaderId": 12,
    "title": "스프링 스터디원 모집",
    "category": "개발",
    "description": "백엔드 스터디를 함께할 팀원을 구합니다.",
    "goal": "포트폴리오 프로젝트 완성",
    "method": "매주 발표와 코드 리뷰",
    "meetingType": "ONLINE",
    "location": null,
    "onlineLink": "https://meet.example.com/moigo",
    "meetingDay": "매주 화요일",
    "capacity": 5,
    "recruitmentDeadline": "2026-08-30",
    "expectedDuration": "8주",
    "conditions": "Java 기초 지식 필요",
    "status": "RECRUITING",
    "visibility": "VISIBLE",
    "groupId": 18,
    "createdAt": "2026-07-21T10:00:00",
    "updatedAt": "2026-07-21T10:00:00"
  },
  "message": null
}
```

### 모집글 노출 상태 변경

`PATCH /api/admin/recruitments/{recruitmentId}/visibility`

```json
{
  "visibility": "HIDDEN",
  "reason": "운영 정책 위반 콘텐츠 확인"
}
```

| Body | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `visibility` | `VISIBLE` \| `HIDDEN` | 예 | 모집 상태와 별도로 관리 |
| `reason` | string | 예 | 공백 제외 10~500자 제안 |

#### 성공 — 200

응답 `data`는 모집글 상세 조회와 같다.

### 모집글 운영 종료

`POST /api/admin/recruitments/{recruitmentId}/end`

```json
{
  "reason": "운영 정책 위반으로 스터디 종료"
}
```

- 모집글과 연결 그룹을 함께 `ENDED`로 변경한다.
- 이미 종료된 경우 현재 상태를 반환하고 운영 이력을 중복 생성하지 않는다.

#### 성공 — 200

응답 `data`는 모집글 상세 조회와 같다.

## 그룹 관리

### 그룹 목록 조회

`GET /api/admin/groups?keyword=스프링&postId=37&leaderId=12&status=ACTIVE&page=0&size=20`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `keyword` | string | 아니요 | 그룹명 부분 일치 검색 |
| `postId` | number | 아니요 | 연결 모집글 ID |
| `leaderId` | number | 아니요 | 활성 리더 회원 ID |
| `status` | `ACTIVE` \| `ENDED` | 아니요 | 그룹 상태 |
| `page` | number | 아니요 | 기본값 `0` |
| `size` | number | 아니요 | 기본값 `20`, 최대 `100` |

목록 응답은 공통 페이지 형식을 사용하며 각 항목에 `groupId`, `postId`, `name`, `status`, `leaderId`, `activeMemberCount`, `createdAt`을 포함한다.

### 그룹 상세 조회

`GET /api/admin/groups/{groupId}`

#### 성공 — 200

```json
{
  "success": true,
  "data": {
    "groupId": 18,
    "postId": 37,
    "name": "스프링 스터디",
    "status": "ACTIVE",
    "createdAt": "2026-07-21T10:00:00",
    "members": [
      {
        "memberId": 12,
        "role": "LEADER",
        "status": "ACTIVE",
        "joinedAt": "2026-07-21T10:00:00"
      }
    ]
  },
  "message": null
}
```

`role`은 `LEADER`, `MANAGER`, `MEMBER` 중 하나다. `LEADER`와 `MANAGER`는 일반 사용자 API(출석·활동)에서 동일한 관리 권한을 갖는 별개 역할이므로 화면과 필터에서 둘 다 노출한다.

### 그룹 일정 목록 조회

`GET /api/admin/groups/{groupId}/schedules?scope=all&page=0&size=20`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `scope` | `all` \| `upcoming` \| `past` | 아니요 | 기본값 `all` |
| `page` | number | 아니요 | 기본값 `0` |
| `size` | number | 아니요 | 기본값 `20`, 최대 `100` |

일정 항목에는 `scheduleId`, `title`, `scheduledAt`, `location`, `onlineLink`, `responseDeadline`을 포함한다.

### 그룹 운영 종료

`POST /api/admin/groups/{groupId}/end`

```json
{
  "reason": "운영 정책 위반으로 그룹 종료"
}
```

- 연결 모집글이 있으면 모집글도 `ENDED`로 변경한다.
- 이미 종료된 경우 현재 상태를 반환하고 운영 이력을 중복 생성하지 않는다.

#### 성공 — 200

응답 `data`는 그룹 상세 조회와 같다.

## 출석 관리

### 일정 출석 요약 조회

`GET /api/admin/schedules/{scheduleId}/attendance-summary`

#### 성공 — 200

```json
{
  "success": true,
  "data": {
    "scheduleId": 44,
    "groupId": 18,
    "totalCount": 4,
    "presentCount": 2,
    "lateCount": 1,
    "absentCount": 1,
    "excusedCount": 0,
    "members": [
      {
        "memberId": 12,
        "status": "PRESENT",
        "checkedBy": 12,
        "checkedAt": "2026-07-23T19:05:00"
      }
    ]
  },
  "message": null
}
```

이 API는 조회만 제공한다. 관리자용 출석 생성·수정·삭제 API는 이번 초안에 포함하지 않는다.

## 활동 관리

### 활동 기록 조회

`GET /api/admin/schedules/{scheduleId}/activity`

#### 성공 — 200

```json
{
  "success": true,
  "data": {
    "activityRecordId": 1,
    "scheduleId": 10,
    "authorId": 1,
    "topic": "1주차 스터디",
    "content": "React 기초 학습",
    "assignment": "다음 주까지 컴포넌트 실습",
    "nextPreparation": "Hooks 예습",
    "referenceLinks": "https://example.com/docs",
    "createdAt": "2026-07-21T09:00:00",
    "updatedAt": "2026-07-21T09:00:00"
  },
  "message": null
}
```

해당 일정에 활동 기록이 없으면 `404`(`ACTIVITY_RECORD_NOT_FOUND`)를 반환한다. 일반 사용자용 API와 달리 요청자가 그 그룹의 그룹원인지는 검증하지 않는다.

### 활동 리뷰 목록 조회

`GET /api/admin/activity-records/{activityRecordId}/reviews`

#### 성공 — 200

```json
{
  "success": true,
  "data": [
    {
      "reviewId": 1,
      "activityRecordId": 1,
      "memberId": 5,
      "comment": "정리가 잘 되어 있어서 좋았어요",
      "createdAt": "2026-07-21T09:10:00"
    }
  ],
  "message": null
}
```

리뷰가 없으면 빈 배열을 반환한다. 이 초안은 조회만 제공하고 부적절한 리뷰를 숨기거나 삭제하는 조치는 포함하지 않는다 — 필요하면 모집글 노출 숨김과 같은 방식의 조치를 팀 협의 후 후속 기능으로 추가한다.

## 운영 이력

### 운영 이력 목록 조회

`GET /api/admin/audit-logs?action=RECRUITMENT_HIDDEN&targetType=RECRUITMENT&targetId=37&adminId=1&from=2026-07-01T00:00:00&to=2026-07-31T23:59:59&page=0&size=20`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `action` | string | 아니요 | 운영 작업 종류 |
| `targetType` | `MEMBER` \| `RECRUITMENT` \| `GROUP` | 아니요 | 대상 종류 |
| `targetId` | number | 아니요 | 대상 ID |
| `adminId` | number | 아니요 | 조치한 운영자 ID |
| `from` | datetime | 아니요 | 조회 시작 시각, 포함 |
| `to` | datetime | 아니요 | 조회 종료 시각, 포함 |
| `page` | number | 아니요 | 기본값 `0` |
| `size` | number | 아니요 | 기본값 `20`, 최대 `100` |

목록 응답은 공통 페이지 형식을 사용하며 각 항목에 `auditLogId`, `action`, `targetType`, `targetId`, `adminId`, `reason`, `createdAt`을 포함한다.

### 운영 이력 상세 조회

`GET /api/admin/audit-logs/{auditLogId}`

#### 성공 — 200

```json
{
  "success": true,
  "data": {
    "auditLogId": 91,
    "action": "RECRUITMENT_HIDDEN",
    "targetType": "RECRUITMENT",
    "targetId": 37,
    "adminId": 1,
    "before": {
      "visibility": "VISIBLE"
    },
    "after": {
      "visibility": "HIDDEN"
    },
    "reason": "운영 정책 위반 콘텐츠 확인",
    "createdAt": "2026-07-23T14:30:00"
  },
  "message": null
}
```

운영 이력 생성·수정·삭제 API는 제공하지 않는다. 운영 조치가 성공할 때 서버 내부에서만 생성한다.

## 팀 협의 시 API 확정 항목

1. 회원 제재 상태로 `SUSPENDED`를 도입할지
2. 모집글 노출 상태 `VISIBLE`, `HIDDEN`을 별도 필드로 도입할지
3. 관리자 조치 성공 상태를 모두 `200`으로 통일할지, 상태 변경에 `204`를 사용할지
4. 페이지 응답을 이 문서의 `items` 형식으로 통일할지 Spring Data `Page` 직렬화를 유지할지
5. 운영 이력의 `before`, `after`를 JSON으로 저장할지 정규화된 필드로 저장할지
6. 활동 리뷰(코멘트)에 운영자 숨김·삭제 조치를 도입할지

