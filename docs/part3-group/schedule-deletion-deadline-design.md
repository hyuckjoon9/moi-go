# 일정 삭제 및 응답 마감 변경 설계

> 확정일: 2026-07-21
>
> 상태: 정책 승인 완료, 구현 전

## 목적

미래 일정 삭제와 `responseDeadline` 변경 범위를 정의하고, Part3가 소유하는 일정과 Part4·활동
영역이 소유하는 종속 데이터 사이의 책임을 고정한다. 이 문서는 구현 전에 합의된 정책의 기준이며,
API 구현 시 [`api.md`](api.md)와 각 담당 파트 문서를 함께 갱신한다.

## 범위

이번 설계에 포함한다.

- 미래 일정 물리 삭제
- 응답 마감 설정·변경·제거 전용 `PATCH` API
- Part4의 참석 응답 마감 판정에 필요한 공개 조회 계약
- 출석·활동 데이터의 FK 삭제 정책과 파트 간 책임
- 권한, 시간 경계, 오류, 동시성과 테스트 기준

이번 설계에서 제외한다.

- 과거 일정 삭제
- 일정 `CANCELLED` 상태와 취소 이력
- 알림 발송
- 출석·활동 데이터의 강제 삭제
- 마감 이후 참석 응답 재개방

## 핵심 정책

### 일정 삭제

- `ACTIVE` 그룹의 활성 `LEADER` 또는 `MANAGER`만 삭제할 수 있다.
- `scheduledAt > now`인 미래 일정만 삭제할 수 있다. `scheduledAt == now`는 시작된 일정이다.
- 과거 또는 시작된 일정은 삭제할 수 없다.
- 참석 응답만 있으면 일정과 함께 삭제한다.
- 출석 기록이나 활동 기록이 하나라도 있으면 일정 삭제를 거부한다.
- `ENDED` 그룹에서는 일정을 삭제할 수 없다.
- 삭제 성공 후 같은 식별자로 재요청하면 `SCHEDULE_NOT_FOUND`를 반환한다.

### 응답 마감

- 마감 변경은 일정 전체 수정과 분리한 전용 `PATCH` API로 제공한다.
- `responseDeadline == null`이면 `scheduledAt`을 실질적인 응답 마감으로 사용한다.
- 참석 응답 등록·변경·삭제는 모두 `now < effectiveDeadline`일 때만 허용한다.
- `now == effectiveDeadline`이면 마감된 상태다.
- 기존 유효 마감이 지나기 전에만 마감을 설정·변경·제거할 수 있다.
- 마감 단축·연장·제거로 이미 저장된 참석 응답을 삭제하거나 무효화하지 않는다.
- 기존 유효 마감이 지난 뒤에는 값을 연장하거나 제거해 응답을 재개방할 수 없다.

실질적인 응답 마감은 다음과 같다.

```text
effectiveDeadline =
    responseDeadline != null
        ? responseDeadline
        : scheduledAt
```

## 일정 삭제 API

```http
DELETE /api/groups/{groupId}/schedules/{scheduleId}
```

### 검증 순서

1. 그룹 존재
2. 그룹원 기록 존재
3. 그룹원 활성 상태
4. 그룹 활성 상태
5. `LEADER`·`MANAGER` 역할
6. 일정 존재와 URL의 그룹 소속
7. 미래 일정 여부
8. 출석 기록 존재 여부
9. 활동 기록 존재 여부
10. 삭제 및 즉시 flush

검증 순서는 기존 일정 관리 API와 같은 접근 오류 우선순위를 유지한다. 종속 기록 확인 후 생기는
동시 요청은 DB FK가 최종적으로 차단한다.

### 성공과 오류

| 상황 | HTTP | 오류 코드 |
| --- | --- | --- |
| 삭제 성공 | `204 No Content` | 없음 |
| 그룹 없음 | `404 Not Found` | `GROUP_NOT_FOUND` |
| 일정 없음 또는 다른 그룹 일정 | `404 Not Found` | `SCHEDULE_NOT_FOUND` |
| 비회원 | `403 Forbidden` | `GROUP_ACCESS_DENIED` |
| 탈퇴 그룹원 | `403 Forbidden` | `WITHDRAWN_GROUP_MEMBER` |
| 일반 그룹원 | `403 Forbidden` | `SCHEDULE_MANAGEMENT_FORBIDDEN` |
| 종료 그룹 | `409 Conflict` | `GROUP_ENDED` |
| 과거·시작된 일정 | `409 Conflict` | `SCHEDULE_DELETE_NOT_ALLOWED` |
| 출석·활동 기록 존재 또는 FK 경합 | `409 Conflict` | `SCHEDULE_DELETE_NOT_ALLOWED` |

출석 기록과 활동 기록의 존재를 외부 응답에서 구분하지 않는다. 두 경우 모두 보존해야 할 종속
이력이 있어 삭제할 수 없다는 같은 의미다.

## 응답 마감 변경 API

```http
PATCH /api/groups/{groupId}/schedules/{scheduleId}/response-deadline
Content-Type: application/json
```

설정 또는 변경:

```json
{
  "responseDeadline": "2026-07-25T18:00:00"
}
```

제거:

```json
{
  "responseDeadline": null
}
```

요청에는 `responseDeadline` 속성이 반드시 있어야 한다. 속성 누락은 잘못된 요청이고, 명시적
`null`만 마감 제거를 뜻한다. 성공 시 수정된 전체 `ScheduleResponse`를 `200 OK`로 반환한다.
기존 일정 전체 수정 `PUT`은 계속 `responseDeadline`을 보존한다.

### 권한과 시간 규칙

- `ACTIVE` 그룹의 활성 `LEADER` 또는 `MANAGER`만 변경할 수 있다.
- `scheduledAt > now`인 일정만 변경할 수 있다.
- 현재 유효 마감에 대해 `now < currentEffectiveDeadline`이어야 한다.
- 새 값이 있으면 `now < newResponseDeadline <= scheduledAt`이어야 한다.
- `null`로 제거하면 새 유효 마감은 `scheduledAt`이다.
- 마감 단축·연장, `null`에서 값 설정, 값에서 `null` 제거를 모두 허용하되 위 조건을 만족해야 한다.
- 마감 변경 시 `updatedAt`을 갱신하고 기존 참석 응답은 그대로 둔다.

### 성공과 오류

| 상황 | HTTP | 오류 코드 |
| --- | --- | --- |
| 변경 성공 | `200 OK` | 없음 |
| 요청 속성 누락 | `400 Bad Request` | `INVALID_REQUEST` |
| 새 마감 값의 시간 규칙 위반 | `400 Bad Request` | `INVALID_SCHEDULE_TIME` |
| 기존 유효 마감이 지났거나 일정이 시작됨 | `409 Conflict` | `SCHEDULE_DEADLINE_UPDATE_NOT_ALLOWED` |
| 그룹·일정·접근·역할 오류 | 기존 일정 관리 API와 동일 | 기존 오류 사용 |

## Part4 공개 계약

Part4는 Part3 Repository를 직접 조회하지 않는다. Part3는 참석 응답 가능 여부를 판단하는 데
필요한 읽기 전용 계약을 제공한다.

```java
ScheduleAttendancePolicy getAttendancePolicy(Long scheduleId, Long userId);
```

계약 결과에는 최소한 다음 값이 있어야 한다.

- `scheduleId`
- `groupId`
- `groupStatus`
- 요청 사용자의 활성 그룹원 여부
- `scheduledAt`
- `responseDeadline`
- `effectiveDeadline`

Part4는 응답 등록·변경·삭제 전에 일정 존재, 활성 그룹, 활성 그룹원과
`now < effectiveDeadline`을 검증한다. 마감 이후의 세 동작은 모두
`ATTENDANCE_RESPONSE_CLOSED` (`409 Conflict`)로 거부한다.

일정 삭제 전 사용자 친화적인 오류를 만들기 위해 Part4는 다음 공개 조회를 제공한다.

```java
boolean hasAttendanceRecord(Long scheduleId);
```

참석 응답 존재 여부는 삭제를 막지 않으므로 이 계약에 포함하지 않는다.

## 활동 영역 공개 계약

활동 영역은 Part3 Repository에 결합하지 않고 일정 식별자만 저장한다. 일정 삭제 전 Part3가
호출할 수 있도록 다음 공개 조회를 제공한다.

```java
boolean hasActivityRecord(Long scheduleId);
```

활동 기능이 아직 스켈레톤이므로 활동 기록 구현 시 이 계약과 `RESTRICT` FK를 함께 반영한다.

## 데이터베이스 계약

| 관계 | `ON DELETE` | 이유 |
| --- | --- | --- |
| 일정 → 참석 응답 | `CASCADE` | 미래 일정 삭제 시 임시 응답도 함께 제거 |
| 일정 → 출석 기록 | `RESTRICT` | 출석 이력과 누적 출석률 보존 |
| 일정 → 활동 기록 | `RESTRICT` | 활동 이력 보존 |

공개 조회 계약은 명확한 비즈니스 오류를 위한 사전 검사이고 FK는 동시 요청을 포함한 최종
무결성 보장이다. 사전 검사 뒤 출석·활동 기록이 생성되어 삭제가 실패하면 Part3는 FK 위반을
`SCHEDULE_DELETE_NOT_ALLOWED`로 변환한다. Part3는 Part4·활동 Repository를 직접 사용하지 않는다.

## 동시성

- 마감 변경 전에 정상 저장된 참석 응답은 이후 마감이 단축되어도 유효하다.
- 응답 저장과 마감 변경이 겹쳐도 저장 완료된 응답을 소급 삭제하거나 무효화하지 않는다.
- 일정 삭제와 출석·활동 기록 생성이 겹치면 `RESTRICT` FK가 일정 삭제를 실패시킨다.
- 일정 삭제와 참석 응답 저장이 겹치면 FK가 삭제 또는 저장 중 하나의 일관된 결과를 보장하며,
  삭제된 일정에 대한 후속 응답 저장은 일정 없음으로 거부한다.

## 테스트 책임

### Part3

- 역할·상태별 삭제 및 마감 변경 권한
- 미래·현재·과거 일정 삭제 경계
- 참석 응답 CASCADE와 출석·활동 기록 RESTRICT
- 마감 설정·단축·연장·제거와 재개방 금지
- 명시적 `null`과 속성 누락 구분
- 공개 조회 계약과 오류 응답

### Part4

- `responseDeadline`이 있는 경우와 없는 경우의 유효 마감 계산
- 마감 직전과 동일 시각·이후의 등록·변경·삭제
- 기존 응답 유지
- 비회원·탈퇴 회원·종료 그룹 응답 거부
- 출석 기록 존재 조회 계약

### 활동 담당

- 활동 기록 존재 조회 계약
- 활동 기록이 있는 일정의 FK 삭제 거부

## 구현 순서

1. Part4·활동 공개 계약과 공통 오류 코드를 담당자와 PR 단위로 확인한다.
2. 운영 스키마와 테스트 스키마의 FK를 이 문서와 일치시킨다.
3. 일정 삭제를 테스트 우선으로 구현한다.
4. 응답 마감 `PATCH`를 테스트 우선으로 구현한다.
5. Part4가 공개 일정 정책을 사용해 응답 마감을 검증하도록 연동한다.
6. 관련 테스트, 전체 테스트와 `spotlessCheck`를 실행한다.
7. [`api.md`](api.md), Part4 API 문서와 세션 컨텍스트를 구현 결과에 맞춰 갱신한다.
