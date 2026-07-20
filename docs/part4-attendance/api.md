# 출석 API

## 엔드포인트 목록

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/attendance/schedules/{scheduleId}/answers/insert` | 필요 | 참석 여부 응답 등록 |
| `PUT` | `/attendance/schedules/{scheduleId}/answers/update` | 필요 | 참석 여부 응답 수정 |
| `DELETE` | `/attendance/schedules/{scheduleId}/answers/delete` | 필요 | 참석 여부 응답 삭제 |
| `POST` | `/attendance/schedules/{scheduleId}/records/insert` | 필요 (모집장) | 출석 체크 등록 |
| `PUT` | `/attendance/schedules/{scheduleId}/records/update` | 필요 (모집장) | 출석 체크 수정 |
| `DELETE` | `/attendance/schedules/{scheduleId}/records/{userId}/delete` | 필요 | 출석 기록 삭제 |
| `GET` | `/attendance/schedules/{scheduleId}/records/summary` | 필요 (모집장) | 스케줄 출석 현황 요약 조회 |
| `GET` | `/attendance/users/{userId}/rate` | 필요 | 개인 누적 출석률 조회 |

## 구현 상태 메모

- `userId`/`checkedBy`는 더 이상 쿼리 파라미터로 받지 않는다. `@AuthenticationPrincipal AuthenticatedMember`로 로그인한 사용자 id를 서버가 직접 채운다 (Part1 인증 방식과 동일, `Authorization: Bearer {accessToken}` 헤더 필요). 인증 정보가 없으면 `BusinessException(UNAUTHORIZED)`.
- `checkedBy`가 실제로 해당 스케줄이 속한 그룹의 `LEADER`/`MANAGER`인지에 대한 권한 검증은 아직 없다. 로그인한 사용자면 누구든 다른 사람의 출석을 체크할 수 있는 상태다. `StudySchedule ↔ StudyGroup` 연결이 생긴 뒤 `GroupMember.role` 기준으로 검증 추가 예정.
- 응답 본문은 Part1(`/api/auth`, `/api/members`)과 달리 `ApiResponse` 공통 래퍼로 감싸지 않고 DTO를 그대로 반환한다. Part1과 포맷을 맞추려면 추후 정리가 필요하다.
- 서비스단에서 응답/기록을 못 찾으면 `EntityNotFoundException`을 던지는데, `GlobalExceptionHandler`는 `BusinessException`/`MethodArgumentNotValidException`/`IllegalArgumentException`만 처리한다. 즉 현재는 404로 내려가지 않고 처리되지 않은 예외로 500이 발생한다.
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

## POST /attendance/schedules/{scheduleId}/answers/insert

멤버가 스케줄의 참석 여부를 등록한다. 응답하는 사용자는 인증 토큰에서 가져온다.

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

## PUT /attendance/schedules/{scheduleId}/answers/update

기존 참석 여부 응답을 수정한다. (예: `UNDECIDED` → `ATTEND`)

### Request

```json
{
  "response": "ABSENT"
}
```

### Response 200

`POST .../answers/insert`와 같은 형식의 응답을 반환한다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| 처리되지 않음 (500) | 해당 `scheduleId`/`userId` 조합의 응답이 없음 (`EntityNotFoundException`, 미처리) |

## DELETE /attendance/schedules/{scheduleId}/answers/delete

참석 여부 응답을 삭제한다. 삭제 대상은 인증 토큰의 사용자 자신이다.

### Response 204

본문 없음.

## POST /attendance/schedules/{scheduleId}/records/insert

모집장이 스케줄의 특정 멤버 출석 상태를 체크한다. `checkedBy`는 인증 토큰에서 가져온다 (현재는 로그인한 사용자면 누구나 호출 가능, 모집장 권한 검증은 미구현).

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

## PUT /attendance/schedules/{scheduleId}/records/update

기존 출석 체크 상태를 수정한다. 수정 시마다 `checkedBy`, `checkedAt`이 갱신된다. `checkedBy`는 인증 토큰에서 가져온다.

### Request

`POST .../records/insert`와 같은 형식.

### Response 200

`POST .../records/insert`와 같은 형식의 응답을 반환한다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 인증 실패 |
| 처리되지 않음 (500) | 해당 `scheduleId`/`userId` 조합의 출석 기록이 없음 (`EntityNotFoundException`, 미처리) |

## DELETE /attendance/schedules/{scheduleId}/records/{userId}/delete

출석 기록을 삭제한다.

### Response 204

본문 없음.

## GET /attendance/schedules/{scheduleId}/records/summary

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

## GET /attendance/users/{userId}/rate

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
