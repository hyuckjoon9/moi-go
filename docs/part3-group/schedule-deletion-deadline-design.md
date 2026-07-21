# 일정 삭제·응답 마감 설계

> 확정·Part3 구현: 2026-07-21

## 목적과 상태

Part3 일정과 Part4 참석·출석, 활동 기록 사이의 삭제·응답 마감 책임을 고정한다. Part3의 삭제, 마감 PATCH, 공개 조회 포트와 FK 통합 검증은 구현됐다. Part4 참석 응답의 마감 적용은 다음 연동 작업이다.

API 오류와 요청은 [`api.md`](api.md), FK는 [`erd.md`](erd.md)를 기준으로 한다.

## 핵심 정책

### 일정 삭제

- 활성 그룹의 활성 `LEADER`·`MANAGER`만 `scheduledAt > now`인 일정을 삭제한다.
- 참석 응답만 있으면 일정과 함께 삭제한다.
- 출석 또는 활동 기록이 있으면 삭제를 거부한다.
- 서비스 사전 조회는 명확한 오류를 제공하고 DB `RESTRICT`는 동시 요청의 최종 무결성을 보장한다.
- 삭제 시 즉시 flush하고 FK 위반도 `SCHEDULE_DELETE_NOT_ALLOWED`로 변환한다.

검증 순서는 그룹 → 그룹원 → 탈퇴 → 그룹 상태 → 역할 → 그룹에 속한 일정 → 미래 여부 → 출석 기록 → 활동 기록 → 삭제다.

### 응답 마감

```text
effectiveDeadline = responseDeadline != null ? responseDeadline : scheduledAt
```

- 응답 등록·변경·삭제는 모두 `now < effectiveDeadline`일 때만 허용한다.
- `now == effectiveDeadline`부터 마감이다.
- 일정과 현재 유효 마감이 지나기 전에만 마감을 설정·변경·제거할 수 있다.
- 새 값이 있으면 `now < newDeadline <= scheduledAt`이어야 한다.
- 마감 변경은 기존 응답을 삭제·무효화하지 않고, 지난 마감을 재개방하지 않는다.
- 명시적 `null`은 제거이며 요청 속성 누락은 잘못된 요청이다.

## 공개 포트

Part4는 Part3 Repository 대신 다음 읽기 계약을 사용한다.

```java
ScheduleAttendancePolicy getAttendancePolicy(Long scheduleId, Long userId);
```

정책은 `scheduleId`, `groupId`, `groupStatus`, 활성 그룹원 여부, `scheduledAt`, `responseDeadline`과 계산된 `effectiveDeadline()`을 제공한다. Part4는 참석 응답 세 동작 전에 활성 그룹·그룹원과 마감을 검사하고 닫힌 경우 `ATTENDANCE_RESPONSE_CLOSED`를 반환해야 한다.

Part3 삭제 서비스는 외부 Repository 대신 다음 포트를 사용한다.

```java
AttendanceRecordLookup.existsByScheduleId(Long scheduleId);
ActivityRecordLookup.existsByScheduleId(Long scheduleId);
```

참석 응답 존재는 삭제를 막지 않으므로 별도 조회하지 않는다.

## 동시성과 데이터 보존

- 마감 단축 전에 저장된 응답은 유지한다.
- 응답 저장과 마감 변경이 겹쳐도 완료된 응답을 소급 삭제하지 않는다.
- 일정 삭제와 출석·활동 생성이 겹치면 `RESTRICT` FK가 삭제를 막는다.
- 일정 삭제와 참석 응답 저장이 겹치면 FK가 일관된 한 결과를 보장한다.

## 검증 책임

- Part3: 역할·상태·시간별 삭제와 마감 변경, 명시적 `null`, 공개 포트, CASCADE·RESTRICT
- Part4: 유효 마감 계산, 등록·변경·삭제 경계, 기존 응답 유지, 그룹 접근 정책
- 활동 영역: 기록 존재 조회와 활동 기록의 삭제 보호

`schedule-fk-verification.sql`은 운영 DDL 적용 전 FK 정책을 점검하는 보조 자료다.
