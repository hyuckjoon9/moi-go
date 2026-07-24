# 출석 API

## 엔드포인트 목록

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/attendance/schedules/{scheduleId}/answers` | 필요 | 참석 여부 응답 등록 |
| `PUT` | `/api/attendance/schedules/{scheduleId}/answers` | 필요 | 참석 여부 응답 수정 |
| `DELETE` | `/api/attendance/schedules/{scheduleId}/answers` | 필요 | 참석 여부 응답 삭제 |
| `GET` | `/api/attendance/schedules/{scheduleId}/answers/summary` | 필요 (그룹 활성 LEADER/MANAGER) | 그룹원별 참석 여부 응답(RSVP) 현황 조회 |
| `POST` | `/api/attendance/schedules/{scheduleId}/records` | 필요 (그룹 활성 LEADER/MANAGER) | 출석 체크 등록 |
| `PUT` | `/api/attendance/schedules/{scheduleId}/records` | 필요 (그룹 활성 LEADER/MANAGER) | 출석 체크 수정 |
| `DELETE` | `/api/attendance/schedules/{scheduleId}/records/{userId}` | 필요 (그룹 활성 LEADER/MANAGER) | 출석 기록 삭제 |
| `GET` | `/api/attendance/schedules/{scheduleId}/records/summary` | 필요 (그룹 활성 LEADER/MANAGER) | 스케줄 출석 현황 요약 조회 |
| `GET` | `/api/attendance/users/{userId}/rate` | 필요 (본인만) | 개인 누적 출석률 조회 |
| `GET` | `/api/attendance/groups/{groupId}/rates` | 필요 (그룹 활성 LEADER/MANAGER) | 그룹원별 출석률 목록 조회 |

## 구현 상태 메모

- `userId`/`checkedBy`는 쿼리 파라미터로 받지 않는다. `@AuthenticationPrincipal AuthenticatedMember`로 로그인한 사용자 id를 서버가 직접 채운다 (Part1 인증 방식과 동일, `Authorization: Bearer {accessToken}` 헤더 필요). 인증 정보가 없으면 `401`.
- 출석 체크 등록·수정·삭제·요약 조회(`POST`/`PUT`/`DELETE .../records/{userId}`/`GET .../records/summary`)는 모두 `StudySchedule → StudyGroup → GroupMember` 조회로 요청자가 해당 스케줄 그룹의 활성 `LEADER`/`MANAGER`인지 검증한다(Activity 도메인과 동일한 패턴). 검증 순서는 일정 없음(`SCHEDULE_NOT_FOUND`) → 그룹원 아님(`GROUP_ACCESS_DENIED`) → 탈퇴 그룹원(`WITHDRAWN_GROUP_MEMBER`) → 권한 없음(`ATTENDANCE_MANAGEMENT_FORBIDDEN`) 순이다.
- `GET /users/{userId}/rate`는 요청자 본인의 출석률만 조회할 수 있다. 경로의 `userId`가 인증된 사용자 id와 다르면 `403`(`ATTENDANCE_RATE_ACCESS_DENIED`)이다. 이 API는 그룹 스코프 없이 사용자 전체 스케줄을 집계하므로 본인 조회로 한정했다.
- 모집장이 그룹원별 출석률을 보려면 `GET /groups/{groupId}/rates`를 쓴다. 권한 판단은 Part3의 공개 포트 `StudyGroupAttendanceRatePolicyReader.getAttendanceRatePolicy(groupId, requesterId)`로 위임한다 — `GroupMemberRepository`를 직접 조회하지 않는다. 이 포트가 그룹 없음(`GROUP_NOT_FOUND`)·비그룹원(`GROUP_ACCESS_DENIED`)·탈퇴 그룹원(`WITHDRAWN_GROUP_MEMBER`)을 Part3 오류로 먼저 처리하고, `StudyGroupAttendanceRatePolicy.canViewAllAttendanceRates()`가 `false`면(그룹이 `ACTIVE`가 아니거나 요청자가 `LEADER`/`MANAGER`가 아니면) `ATTENDANCE_MANAGEMENT_FORBIDDEN`(403)으로 거부한다. 종료된(`ENDED`) 그룹은 모집장이어도 조회할 수 없다.
- 그룹원별 출석률을 만드는 집계 자체는 `Attendance` 패키지 안에서 처리한다. `StudyScheduleRepository.findAllByStudyGroupIdOrderByScheduledAtAsc(groupId)`로 이 그룹의 `scheduleId` 목록을 구하고, `AttendanceRecordRepository.findByScheduleIdIn(scheduleIds)`로 이 그룹의 스케줄에서 발생한 출석 기록만 집계한다(다른 그룹 일정은 섞이지 않는다). "그룹원별 목록"을 만들기 위한 활성 그룹원 조회(`GroupMemberRepository.findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc`)는 권한 판단과 별개라 계속 직접 조회한다 — Part3 경계로 완전히 분리하려면 활성 그룹원 목록 조회 포트가 추가로 필요하다. 응답은 그룹의 활성 그룹원 전원을 포함하며(출석 기록이 없는 멤버는 0.0), 순서는 역할 → 가입 시각 → 사용자 id 순이다.
- 참석 응답 등록·변경·삭제는 Part3의 공개 포트 `ScheduleAttendancePolicyReader.getAttendancePolicy(scheduleId, userId)`로 조회한 `effectiveDeadline`(=`responseDeadline`이 있으면 그 값, 없으면 `scheduledAt`) 이전인지 검증한다. `now < effectiveDeadline`이 아니면 `409`(`ATTENDANCE_RESPONSE_CLOSED`)로 거부한다. 이 포트가 스케줄 존재·활성 그룹원 여부도 함께 검증하므로 `SCHEDULE_NOT_FOUND`/`GROUP_ACCESS_DENIED`/`WITHDRAWN_GROUP_MEMBER`도 이 세 API에서 발생할 수 있다(Part3 Repository를 직접 참조하지 않는다, `docs/part3-group/schedule-deletion-deadline-design.md` 참고).
- 출석 체크 등록(`POST .../records`)은 일정이 시작되기 전에는 할 수 없다. `now < schedule.scheduledAt`이면 `409`(`ATTENDANCE_CHECK_NOT_STARTED`)로 거부한다. 수정(`PUT .../records`)에는 이 검증이 없다 — 이미 체크 기록이 존재한다는 것 자체가 시작 시각이 지났음을 보장하므로 정정은 시간 제한 없이 허용한다. 자세한 배경은 `docs/part4-attendance/attendance-window-design.md` 참고.
- **자동 결석 처리**: 일정 시작 후 2시간이 지나도 체크되지 않은 활성 그룹원은 시스템이 자동으로 채운다 — 참여 응답이 `ABSENT`였으면 `EXCUSED`로, `ATTEND`/`UNDECIDED`/무응답이면 `ABSENT`로 기록하며 이때 `checkedBy`는 `null`이다. 트리거는 두 가지다: (1) `GET .../records/summary`, `GET /users/{userId}/rate`, `GET /groups/{groupId}/rates` 조회 시점에 그 자리에서 먼저 채운 뒤 집계, (2) 아무도 조회하지 않는 경우를 대비해 1시간마다 도는 안전망 배치(`AttendanceAutoProcessingScheduler`). 자동 채워진 기록도 모집장이 `PUT .../records`로 언제든 정정할 수 있다.
- `GET .../answers/summary`는 출석 체크 요약(`GET .../records/summary`)과 동일하게 그룹의 활성 `LEADER`/`MANAGER`만 조회할 수 있다(`validateManager`, `ATTENDANCE_MANAGEMENT_FORBIDDEN`). 그룹의 활성 그룹원 전원을 기준으로 하며, 아직 응답하지 않은 멤버도 `response: "UNDECIDED"`로 채워서 포함한다 — 이 값은 DB에 저장된 값이 아니라 응답 조합 시점의 기본값이다. 실제로 응답을 제출했는지는 `respondedAt`이 `null`인지로 구분한다(미제출이면 `null`).
- 같은 `scheduleId`/`userId` 조합으로 이미 응답/체크가 있으면 등록(`POST`)은 `409`로 거부한다. 수정은 `PUT`을 쓴다.
- 대상이 없으면(`PUT`/`DELETE`) `404`를 반환한다.
- 응답 본문은 Part1(`/api/auth`, `/api/members`)과 달리 `ApiResponse` 공통 래퍼로 감싸지 않고 DTO를 그대로 반환한다. Part1과 포맷을 맞추려면 추후 정리가 필요하다.
- `AttendanceRecord`/`AttendanceAnswer`의 `scheduleId`, `userId`는 `@ManyToOne` 연관관계가 아니라 순수 FK(`Long`) 컬럼이다. `StudySchedule` 엔티티가 아직 스켈레톤 상태라, 완성되면 `scheduleId`/`userId` 둘 다 한 번에 연관관계로 전환할 예정.

## Enum

### AttendanceStatus (출석 체크 상태)

| 값 | 설명 |
| --- | --- |
| `PRESENT` | 출석 |
| `LATE` | 지각 |
| `ABSENT` | 결석 |
| `EXCUSED` | 사유 결석 (인정 결석) |

### AttendanceResponse (참석 여부 응답)

| 값 | 설명 |
| --- | --- |
| `ATTEND` | 참석 |
| `ABSENT` | 불참 |
| `UNDECIDED` | 미정 |

## POST /api/attendance/schedules/{scheduleId}/answers

멤버가 스케줄의 참석 여부를 등록한다. 응답하는 사용자는 인증 토큰에서 가져온다. 응답 마감(`effectiveDeadline`) 전에만 등록할 수 있다.

### Request

```json
{
  "response": "ATTEND"
}
```

### Response 201

```json
{
  "id": 1,
  "scheduleId": 10,
  "userId": 5,
  "response": "ATTEND",
  "respondedAt": "2026-07-20T09:00:00"
}
```

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `409` | 응답 마감(`effectiveDeadline`)이 지남 (`ATTENDANCE_RESPONSE_CLOSED`) |
| `409` | 해당 `scheduleId`/`userId` 조합의 응답이 이미 존재함 (`DUPLICATE_ATTENDANCE_ANSWER`) |

## PUT /api/attendance/schedules/{scheduleId}/answers

기존 참석 여부 응답을 수정한다. (예: `UNDECIDED` → `ATTEND`) 응답 마감(`effectiveDeadline`) 전에만 수정할 수 있다.

### Request

```json
{
  "response": "ABSENT"
}
```

### Response 200

`POST .../answers`와 같은 형식의 응답을 반환한다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `409` | 응답 마감(`effectiveDeadline`)이 지남 (`ATTENDANCE_RESPONSE_CLOSED`) |
| `404` | 해당 `scheduleId`/`userId` 조합의 응답이 없음 (`ATTENDANCE_ANSWER_NOT_FOUND`) |

## DELETE /api/attendance/schedules/{scheduleId}/answers

참석 여부 응답을 삭제한다. 삭제 대상은 인증 토큰의 사용자 자신이다. 응답 마감(`effectiveDeadline`) 전에만 삭제할 수 있다.

### Response 204

본문 없음.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `409` | 응답 마감(`effectiveDeadline`)이 지남 (`ATTENDANCE_RESPONSE_CLOSED`) |
| `404` | 해당 `scheduleId`/`userId` 조합의 응답이 없음 (`ATTENDANCE_ANSWER_NOT_FOUND`) |

## GET /api/attendance/schedules/{scheduleId}/answers/summary

일정 그룹의 활성 `LEADER`/`MANAGER`가 그룹원별 참석 여부 응답(RSVP) 현황을 조회한다. 그룹의 활성 그룹원 전원을 포함하며, 아직 응답하지 않은 멤버는 `response: "UNDECIDED"`(기본값), `respondedAt: null`로 표시된다.

### Response 200

```json
{
  "scheduleId": 10,
  "totalMemberCount": 3,
  "attendCount": 1,
  "absentCount": 0,
  "undecidedCount": 2,
  "members": [
    {
      "userId": 5,
      "response": "ATTEND",
      "respondedAt": "2026-07-20T09:00:00"
    },
    {
      "userId": 6,
      "response": "UNDECIDED",
      "respondedAt": "2026-07-19T18:30:00"
    },
    {
      "userId": 7,
      "response": "UNDECIDED",
      "respondedAt": null
    }
  ]
}
```

`userId: 6`처럼 실제로 `UNDECIDED`를 명시적으로 응답 제출한 경우와, `userId: 7`처럼 아예 응답한 적 없는 경우는 `response` 값만으로는 구분되지 않는다 — `respondedAt`으로 구분한다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `403` | 요청자가 `LEADER`/`MANAGER`가 아님 (`ATTENDANCE_MANAGEMENT_FORBIDDEN`) |

## POST /api/attendance/schedules/{scheduleId}/records

일정 그룹의 활성 `LEADER`/`MANAGER`가 스케줄의 특정 멤버 출석 상태를 체크한다. `checkedBy`는 인증 토큰에서 가져온다.

### Request

```json
{
  "userId": 5,
  "status": "PRESENT"
}
```

### Request 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | `Long` | 예 | 출석 대상 사용자 id |
| `status` | `AttendanceStatus` | 예 | 출석 상태 |

### Response 201

```json
{
  "id": 1,
  "scheduleId": 10,
  "userId": 5,
  "status": "PRESENT",
  "checkedBy": 2,
  "checkedAt": "2026-07-20T09:00:00"
}
```

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `403` | 요청자가 `LEADER`/`MANAGER`가 아님 (`ATTENDANCE_MANAGEMENT_FORBIDDEN`) |
| `409` | 일정 시작 전임 (`ATTENDANCE_CHECK_NOT_STARTED`) |
| `409` | 해당 `scheduleId`/`userId` 조합의 출석 기록이 이미 존재함 (`DUPLICATE_ATTENDANCE_RECORD`) |

## PUT /api/attendance/schedules/{scheduleId}/records

일정 그룹의 활성 `LEADER`/`MANAGER`가 기존 출석 체크 상태를 수정한다. 수정 시마다 `checkedBy`, `checkedAt`이 갱신된다. `checkedBy`는 인증 토큰에서 가져온다.

### Request

`POST .../records`와 같은 형식.

### Response 200

`POST .../records`와 같은 형식의 응답을 반환한다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `403` | 요청자가 `LEADER`/`MANAGER`가 아님 (`ATTENDANCE_MANAGEMENT_FORBIDDEN`) |
| `404` | 해당 `scheduleId`/`userId` 조합의 출석 기록이 없음 (`ATTENDANCE_RECORD_NOT_FOUND`) |

## DELETE /api/attendance/schedules/{scheduleId}/records/{userId}

일정 그룹의 활성 `LEADER`/`MANAGER`가 출석 기록을 삭제한다.

### Response 204

본문 없음.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `403` | 요청자가 `LEADER`/`MANAGER`가 아님 (`ATTENDANCE_MANAGEMENT_FORBIDDEN`) |
| `404` | 해당 `scheduleId`/`userId` 조합의 출석 기록이 없음 (`ATTENDANCE_RECORD_NOT_FOUND`) |

## GET /api/attendance/schedules/{scheduleId}/records/summary

일정 그룹의 활성 `LEADER`/`MANAGER`가 스케줄의 출석 현황을 상태별 인원 수와 개별 멤버 내역으로 조회한다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `scheduleId`의 일정이 없음 (`SCHEDULE_NOT_FOUND`) |
| `403` | 요청자가 일정 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `403` | 요청자가 `LEADER`/`MANAGER`가 아님 (`ATTENDANCE_MANAGEMENT_FORBIDDEN`) |

### Response 200

```json
{
  "scheduleId": 10,
  "totalCount": 3,
  "presentCount": 2,
  "lateCount": 0,
  "absentCount": 1,
  "excusedCount": 0,
  "members": [
    {
      "userId": 5,
      "status": "PRESENT",
      "checkedBy": 2,
      "checkedAt": "2026-07-20T09:00:00"
    },
    {
      "userId": 6,
      "status": "PRESENT",
      "checkedBy": 2,
      "checkedAt": "2026-07-20T09:00:00"
    },
    {
      "userId": 7,
      "status": "ABSENT",
      "checkedBy": 2,
      "checkedAt": "2026-07-20T09:05:00"
    }
  ]
}
```

## GET /api/attendance/users/{userId}/rate

요청자 본인의 전체 스케줄 기준 누적 출석률을 조회한다. 경로의 `userId`는 인증된 사용자 자신이어야 한다.

### Response 200

```json
{
  "userId": 5,
  "totalCount": 10,
  "presentCount": 7,
  "lateCount": 1,
  "absentCount": 1,
  "excusedCount": 1,
  "attendanceRate": 70.0
}
```

`attendanceRate`는 `presentCount / totalCount * 100`(%)이며, 기록이 없으면 `0.0`이다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `403` | 경로의 `userId`가 요청자 본인이 아님 (`ATTENDANCE_RATE_ACCESS_DENIED`) |

## GET /api/attendance/groups/{groupId}/rates

`ACTIVE` 상태인 그룹의 활성 `LEADER`/`MANAGER`가 그룹원별 출석률을 조회한다. `GET /users/{userId}/rate`와 달리 이 그룹의 스케줄에서 발생한 출석 기록만 집계하고, 응답이 없거나 출석 체크가 안 된 멤버도 `0.0`으로 포함해 그룹의 활성 그룹원 전원을 보여준다.

### Response 200

```json
[
  {
    "userId": 20,
    "totalCount": 4,
    "presentCount": 3,
    "lateCount": 0,
    "absentCount": 1,
    "excusedCount": 0,
    "attendanceRate": 75.0
  },
  {
    "userId": 21,
    "totalCount": 4,
    "presentCount": 4,
    "lateCount": 0,
    "absentCount": 0,
    "excusedCount": 0,
    "attendanceRate": 100.0
  }
]
```

순서는 역할(`LEADER` → `MANAGER` → `MEMBER`) → 가입 시각 → 사용자 id 순이다. 그룹에 일정이 하나도 없으면 전원 `totalCount: 0`, `attendanceRate: 0.0`으로 반환한다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| `404` | 해당 `groupId`의 그룹이 없음 (`GROUP_NOT_FOUND`) |
| `403` | 요청자가 그룹의 그룹원이 아님 (`GROUP_ACCESS_DENIED`) |
| `403` | 요청자가 탈퇴한 그룹원임 (`WITHDRAWN_GROUP_MEMBER`) |
| `403` | 그룹이 `ACTIVE`가 아니거나 요청자가 `LEADER`/`MANAGER`가 아님 (`ATTENDANCE_MANAGEMENT_FORBIDDEN`) |
