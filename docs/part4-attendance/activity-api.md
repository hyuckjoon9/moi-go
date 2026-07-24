# 활동 기록·리뷰 API

## 엔드포인트 목록

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/activity/schedules/{scheduleId}/record` | 필요 (그룹 LEADER/MANAGER) | 활동 기록 등록 |
| `PUT` | `/api/activity/schedules/{scheduleId}/record` | 필요 (그룹 LEADER/MANAGER) | 활동 기록 수정 |
| `DELETE` | `/api/activity/schedules/{scheduleId}/record` | 필요 (그룹 LEADER/MANAGER) | 활동 기록 삭제 |
| `GET` | `/api/activity/schedules/{scheduleId}/record` | 필요 (그룹 활성 멤버) | 활동 기록 조회 |
| `POST` | `/api/activity/records/{activityRecordId}/reviews` | 필요 (그룹 활성 멤버) | 활동 기록 리뷰 작성 |
| `DELETE` | `/api/activity/records/{activityRecordId}/reviews` | 필요 (그룹 활성 멤버 · 작성자 본인) | 리뷰 삭제 |
| `DELETE` | `/api/activity/records/{activityRecordId}/reviews/{reviewId}` | 필요 (그룹 LEADER/MANAGER) | 부적절한 리뷰 삭제 (작성자 무관) |
| `GET` | `/api/activity/records/{activityRecordId}/reviews` | 필요 (그룹 활성 멤버) | 리뷰 목록 조회 |

## 구현 상태 메모

- `authorId`/`userId`는 쿼리 파라미터로 받지 않는다. `@AuthenticationPrincipal AuthenticatedMember`로 로그인한 사용자 id를 서버가 직접 채운다 (attendance와 동일한 방식, `Authorization: Bearer {accessToken}` 헤더 필요). 인증 정보가 없으면 `401`.
- 활동 기록 등록·수정·삭제는 `StudySchedule → StudyGroup → GroupMember` 조회로 요청자가 해당 일정 그룹의 활성 `LEADER`/`MANAGER`인지 검증한다. 원래 작성자인지는 더 이상 확인하지 않는다 — 같은 그룹의 다른 `LEADER`/`MANAGER`도 수정·삭제할 수 있다(Schedule 도메인의 일정 관리 권한과 동일한 정책). 검증 순서는 일정 없음(`SCHEDULE_NOT_FOUND`) → 그룹원 아님(`GROUP_ACCESS_DENIED`) → 탈퇴 그룹원(`WITHDRAWN_GROUP_MEMBER`) → 권한 없음(`ACTIVITY_RECORD_ACCESS_DENIED`) 순이다.
- 리뷰 작성·삭제는 활동 기록 → `scheduleId` → `StudyGroup` → `GroupMember` 조회로 요청자가 해당 일정 그룹의 활성 그룹원인지 검증한다. 역할(`LEADER`/`MANAGER`/`MEMBER`)은 가리지 않는다 — 활동 기록과 달리 리뷰는 역할 무관하게 그룹원이면 누구나 남길 수 있도록 의도적으로 설계했다. 검증 순서는 활동 기록 없음(`ACTIVITY_RECORD_NOT_FOUND`) → 그룹원 아님(`GROUP_ACCESS_DENIED`) → 탈퇴 그룹원(`WITHDRAWN_GROUP_MEMBER`) 순이다.
- 활동 기록 조회·리뷰 목록 조회도 다른 그룹의 기록이 노출되지 않도록 동일하게 활성 그룹원 검증을 거친다(역할 무관). 활동 기록 조회는 일정 → `StudyGroup` → `GroupMember` 순으로, 리뷰 목록 조회는 활동 기록 → `scheduleId` → `StudyGroup` → `GroupMember` 순으로 조회해 검증한다.
- `DELETE .../reviews/{reviewId}`는 작성자 본인 삭제(`DELETE .../reviews`)와 별개 엔드포인트다. 활동 기록 등록·수정·삭제와 동일한 `validateManager` 검증을 재사용해 요청자가 그룹의 활성 `LEADER`/`MANAGER`인지 확인하며, 작성자 본인 여부와 무관하게 어떤 그룹원의 리뷰든 삭제할 수 있다(부적절한 리뷰 신고·모더레이션 목적). `reviewId`가 경로의 `activityRecordId` 소속이 아니면(다른 그룹/기록의 리뷰 id를 잘못 넣은 경우) `404`(`ACTIVITY_REVIEW_NOT_FOUND`)로 거부한다.
- 활동 기록 등록(`POST .../record`)은 일정이 시작되기 전에는 할 수 없다. `now < schedule.scheduledAt`이면 `409`(`ACTIVITY_RECORD_NOT_STARTED`)로 거부한다. 수정(`PUT .../record`)에는 이 검증이 없다 — 이미 기록이 존재한다는 것 자체가 시작 시각이 지났음을 보장하므로 수정은 시간 제한 없이 허용한다(출석 체크와 동일한 논리, `docs/part4-attendance/attendance-window-design.md` 참고). 시작 이후로는 언제 작성해도 제한이 없다.
- 같은 `scheduleId`로 이미 활동 기록이 있으면 등록(`POST`)은 `409`로 거부한다(`DUPLICATE_ACTIVITY_RECORD`). 수정은 `PUT`을 쓴다.
- 같은 `(activityRecordId, userId)` 조합으로 이미 리뷰가 있으면 등록은 `409`로 거부한다(`DUPLICATE_ACTIVITY_REVIEW`).
- 리뷰는 수정 API가 없다. `activity_reviews` 테이블에 `updated_at` 컬럼이 없어 생성 후 불변으로 설계했다. 수정이 필요하면 삭제 후 재작성한다.
- 동시에 같은 요청이 중복 제출되는 경우(더블클릭 등) DB 유니크 제약 위반이 정제되지 않은 `500` 응답으로 노출될 수 있다. 아직 처리하지 않았고 별도로 논의 중이다.
- 응답 본문은 Part1(`/api/auth`, `/api/members`)과 달리 `ApiResponse` 공통 래퍼로 감싸지 않고 DTO를 그대로 반환한다.
- `ActivityRecord`/`ActivityReview`의 `scheduleId`, `authorId`, `activityRecordId`, `userId`는 `@ManyToOne` 연관관계가 아니라 순수 FK(`Long`) 컬럼이다.
- `activity_records.schedule_id` FK는 `study_schedules`에 대해 `ON DELETE RESTRICT`다. 출석 기록과 동일하게, 활동 기록이 남아있는 일정은 삭제할 수 없다 (`docs/part4-attendance/agreement.md` 참고).

## POST /api/activity/schedules/{scheduleId}/record

일정 그룹의 활성 `LEADER`/`MANAGER`가 활동 기록을 처음 등록한다. 작성자(`authorId`)는 인증 토큰에서 가져온다.

### Request

```json
{
  "topic": "1주차 스터디",
  "content": "React 기초 학습",
  "assignment": "다음 주까지 컴포넌트 실습",
  "nextPreparation": "Hooks 예습",
  "referenceLinks": "https://example.com/docs"
}
```

### Request 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `topic` | `String` | 예 (최대 100자) | 활동 주제 |
| `content` | `String` | 예 | 활동 내용 |
| `assignment` | `String` | 아니오 | 과제 |
| `nextPreparation` | `String` | 아니오 | 다음 활동 준비물 |
| `referenceLinks` | `String` | 아니오 | 참고 링크 |

### Response 201

```json
{
  "id": 1,
  "scheduleId": 10,
  "authorId": 1,
  "topic": "1주차 스터디",
  "content": "React 기초 학습",
  "assignment": "다음 주까지 컴포넌트 실습",
  "nextPreparation": "Hooks 예습",
  "referenceLinks": "https://example.com/docs",
  "createdAt": "2026-07-21T09:00:00",
  "updatedAt": "2026-07-21T09:00:00"
}
```

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `403` | 요청자가 `LEADER`/`MANAGER`가 아님 (`ACTIVITY_RECORD_ACCESS_DENIED`) |
| `409` | 일정 시작 전임 (`ACTIVITY_RECORD_NOT_STARTED`) |
| `409` | 해당 `scheduleId`의 활동 기록이 이미 존재함 (`DUPLICATE_ACTIVITY_RECORD`) |

## PUT /api/activity/schedules/{scheduleId}/record

일정 그룹의 활성 `LEADER`/`MANAGER`가 기존 활동 기록을 수정한다.

### Request

`POST .../record`와 같은 형식.

### Response 200

`POST .../record`와 같은 형식의 응답을 반환한다. `updatedAt`이 갱신된다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `403` | 요청자가 `LEADER`/`MANAGER`가 아님 (`ACTIVITY_RECORD_ACCESS_DENIED`) |
| `404` | 해당 `scheduleId`의 활동 기록이 없음 (`ACTIVITY_RECORD_NOT_FOUND`) |

## DELETE /api/activity/schedules/{scheduleId}/record

일정 그룹의 활성 `LEADER`/`MANAGER`가 활동 기록을 삭제한다.

### Response 204

본문 없음.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `403` | 요청자가 `LEADER`/`MANAGER`가 아님 (`ACTIVITY_RECORD_ACCESS_DENIED`) |
| `404` | 해당 `scheduleId`의 활동 기록이 없음 (`ACTIVITY_RECORD_NOT_FOUND`) |

## GET /api/activity/schedules/{scheduleId}/record

일정 그룹의 활성 그룹원(역할 무관)이 활동 기록을 조회한다.

### Response 200

`POST .../record`와 같은 형식의 응답을 반환한다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `404` | 해당 `scheduleId`의 활동 기록이 없음 (`ACTIVITY_RECORD_NOT_FOUND`) |

## POST /api/activity/records/{activityRecordId}/reviews

활동 기록이 속한 일정 그룹의 활성 그룹원(역할 무관)이 리뷰(코멘트)를 작성한다. 작성자는 인증 토큰에서 가져온다.

### Request

```json
{
  "comment": "정리가 잘 되어 있어서 좋았어요"
}
```

### Request 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `comment` | `String` | 예 (최대 300자) | 리뷰 내용 |

### Response 201

```json
{
  "id": 1,
  "activityRecordId": 1,
  "userId": 5,
  "comment": "정리가 잘 되어 있어서 좋았어요",
  "createdAt": "2026-07-21T09:10:00"
}
```

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `activityRecordId`의 활동 기록이 없음 (`ACTIVITY_RECORD_NOT_FOUND`) |
| `404` | 활동 기록이 속한 일정이 없음 (`SCHEDULE_NOT_FOUND`, 정합성이 깨진 예외적 상황) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `409` | 해당 `(activityRecordId, userId)` 조합의 리뷰가 이미 존재함 (`DUPLICATE_ACTIVITY_REVIEW`) |

## DELETE /api/activity/records/{activityRecordId}/reviews

활동 기록이 속한 일정 그룹의 활성 그룹원(역할 무관)이 본인이 남긴 리뷰를 삭제한다. `(activityRecordId, userId)` 조합으로 조회하므로 본인 리뷰만 삭제된다.

### Response 204

본문 없음.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `activityRecordId`의 활동 기록이 없음 (`ACTIVITY_RECORD_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `404` | 요청자가 남긴 리뷰가 없음 (`ACTIVITY_REVIEW_NOT_FOUND`) |

## DELETE /api/activity/records/{activityRecordId}/reviews/{reviewId}

활동 기록이 속한 일정 그룹의 활성 `LEADER`/`MANAGER`가 부적절한 리뷰를 삭제한다. 작성자 본인 여부와 무관하게 `reviewId`로 지정한 리뷰를 삭제할 수 있다.

### Response 204

본문 없음.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `activityRecordId`의 활동 기록이 없음 (`ACTIVITY_RECORD_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `403` | 요청자가 `LEADER`/`MANAGER`가 아님 (`ACTIVITY_RECORD_ACCESS_DENIED`) |
| `404` | 해당 `reviewId`의 리뷰가 없거나, 있어도 `activityRecordId` 소속이 아님 (`ACTIVITY_REVIEW_NOT_FOUND`) |

## GET /api/activity/records/{activityRecordId}/reviews

활동 기록이 속한 일정 그룹의 활성 그룹원(역할 무관)이 리뷰 목록을 조회한다.

### Response 200

```json
[
  {
    "id": 1,
    "activityRecordId": 1,
    "userId": 5,
    "comment": "정리가 잘 되어 있어서 좋았어요",
    "createdAt": "2026-07-21T09:10:00"
  }
]
```

기록은 있는데 리뷰가 없으면 빈 배열을 반환한다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `activityRecordId`의 활동 기록이 없음 (`ACTIVITY_RECORD_NOT_FOUND`) |
| `404` | 활동 기록이 속한 일정이 없음 (`SCHEDULE_NOT_FOUND`, 정합성이 깨진 예외적 상황) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
