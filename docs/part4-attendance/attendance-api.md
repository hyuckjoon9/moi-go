# 출석 API

## 엔드포인트 목록

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/attendance/schedules/{scheduleId}/answers` | 필요 | 참석 여부 응답 등록 |
| `PUT` | `/api/attendance/schedules/{scheduleId}/answers` | 필요 | 참석 여부 응답 수정 |
| `DELETE` | `/api/attendance/schedules/{scheduleId}/answers` | 필요 | 참석 여부 응답 삭제 |
| `POST` | `/api/attendance/schedules/{scheduleId}/records` | 필요 (그룹 활성 LEADER/MANAGER) | 출석 체크 등록 |
| `PUT` | `/api/attendance/schedules/{scheduleId}/records` | 필요 (그룹 활성 LEADER/MANAGER) | 출석 체크 수정 |
| `DELETE` | `/api/attendance/schedules/{scheduleId}/records/{userId}` | 필요 | 출석 기록 삭제 |
| `GET` | `/api/attendance/schedules/{scheduleId}/records/summary` | 필요 (권한 검증 미구현) | 스케줄 출석 현황 요약 조회 |
| `GET` | `/api/attendance/users/{userId}/rate` | 필요 (권한 검증 미구현) | 개인 누적 출석률 조회 |

## 구현 상태 메모

- `userId`/`checkedBy`는 쿼리 파라미터로 받지 않는다. `@AuthenticationPrincipal AuthenticatedMember`로 로그인한 사용자 id를 서버가 직접 채운다 (Part1 인증 방식과 동일, `Authorization: Bearer {accessToken}` 헤더 필요). 인증 정보가 없으면 `401`.
- 출석 체크 등록·수정은 `StudySchedule → StudyGroup → GroupMember` 조회로 `checkedBy`가 해당 스케줄 그룹의 활성 `LEADER`/`MANAGER`인지 검증한다(Activity 도메인과 동일한 패턴). 검증 순서는 일정 없음(`SCHEDULE_NOT_FOUND`) → 그룹원 아님(`GROUP_ACCESS_DENIED`) → 탈퇴 그룹원(`WITHDRAWN_GROUP_MEMBER`) → 권한 없음(`ATTENDANCE_MANAGEMENT_FORBIDDEN`) 순이다.
- `DELETE .../records/{userId}`는 인증된 사용자인지 확인만 하고 요청자가 그 기록의 당사자인지, 권한이 있는지는 확인하지 않는다. 로그인만 했으면 다른 사람의 출석 기록을 지울 수 있는 상태다. 아직 처리하지 않았다.
- `GET .../records/summary`, `GET /users/{userId}/rate`는 로그인 여부와 무관하게 컨트롤러가 인증 정보를 아예 받지 않는다. 요청자가 그룹원인지, 본인 것만 조회 가능한지 등 권한 구분이 전혀 없다. 아직 처리하지 않았다.
- 참석 응답 등록·변경·삭제는 Part3의 공개 포트 `ScheduleAttendancePolicyReader.getAttendancePolicy(scheduleId, userId)`로 조회한 `effectiveDeadline`(=`responseDeadline`이 있으면 그 값, 없으면 `scheduledAt`) 이전인지 검증한다. `now < effectiveDeadline`이 아니면 `409`(`ATTENDANCE_RESPONSE_CLOSED`)로 거부한다. 이 포트가 스케줄 존재·활성 그룹원 여부도 함께 검증하므로 `SCHEDULE_NOT_FOUND`/`GROUP_ACCESS_DENIED`/`WITHDRAWN_GROUP_MEMBER`도 이 세 API에서 발생할 수 있다(Part3 Repository를 직접 참조하지 않는다, `docs/part3-group/schedule-deletion-deadline-design.md` 참고). 출석 체크(모집장)에는 이 검증이 없다 — 아직 논의되지 않은 부분이다.
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

출석 기록을 삭제한다. 요청자가 인증되어 있는지만 확인하고, 그 외 권한 검증은 없다 (위 "구현 상태 메모" 참고).

### Response 204

본문 없음.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `404` | 해당 `scheduleId`/`userId` 조합의 출석 기록이 없음 (`ATTENDANCE_RECORD_NOT_FOUND`) |

## GET /api/attendance/schedules/{scheduleId}/records/summary

모집장이 스케줄의 출석 현황을 상태별 인원 수와 개별 멤버 내역으로 조회한다.

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

특정 사용자의 전체 스케줄 기준 누적 출석률을 조회한다.

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
