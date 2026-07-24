# 출석 체크 시간 정책 & 자동 결석 처리 설계

작성일: 2026-07-23 / 전체 구현 완료: 2026-07-24

`docs/part4-attendance/qa-fixes.md`에서 나온 "출석 체크에 시간 제약이 없다", "회고 작성이 스케줄 시작 전에도 가능하다" 항목을 어떻게 풀지 논의하고 정리한 결과. 아래 정책 1~4번 모두 구현 완료.

## 배경

- 참여 응답(RSVP)은 `ScheduleAttendancePolicy.effectiveDeadline()`으로 마감 이후 응답을 막고 있는데(`AttendanceService.validateResponseOpen`), 출석 체크(`checkAttendance`/`updateAttendance`)와 회고 작성(`ActivityService.createRecord`/`updateRecord`)은 역할 검증(`validateManager`)만 하고 시간 제약이 전혀 없다.
- 회고는 스케줄 시작 이후라면 아무 때나 작성해도 문제없다 (내용 작성이라 늦게 써도 데이터 신뢰성 문제 없음).
- 출석 체크는 반대로, 시간이 한참 지난 뒤에도 상태를 새로 만들거나 바꿀 수 있다는 게 데이터 신뢰성 문제로 이어질 수 있어 별도 정책이 필요하다.

## 결론 (정책)

1. **회고 작성**: 스케줄 시작(`scheduledAt`) 이후로는 제한 없이 허용한다. — ✅ 구현 완료 (`createRecord`에만 `now >= scheduledAt` 검증 추가, `updateRecord`는 출석 체크와 동일한 논리로 제외)
2. **출석 체크**: 스케줄 시작 전에는 불가능하다. — ✅ 구현 완료
3. **자동 결석 처리**: 시작 후 2시간이 지나도 체크되지 않은 그룹원은 시스템이 자동으로 상태를 채운다. — ✅ 구현 완료
   - 사전에 참여 응답(RSVP)에서 `ABSENT`(불참)로 응답한 사람 → 자동으로 `EXCUSED`(사유 결석)
   - `ATTEND`/`UNDECIDED`/무응답인 사람 → 자동으로 `ABSENT`(무단 결석)
   - 탈퇴(`WITHDRAWN`) 그룹원은 대상에서 제외한다 (활성 그룹원만 대상으로, 참여 응답/출석 체크의 기존 검증과 일관되게 결정).
4. 자동 처리 이후에도 모집장은 `updateAttendance`로 언제든 정정할 수 있다 — 별도 시간 제한을 두지 않는다. (정상적으로 참석을 체크했다면 2시간 안에 끝났을 일이라 정정이 필요한 경우는 드물지만, 안전장치로 열어둔다.)

## 구현 방식

### 회고(활동 기록) 시작 시각 검증

- `ActivityService.createRecord`에서 `validateManager` 다음으로 `validateScheduleStarted(scheduleId)`를 호출한다. `now < schedule.getScheduledAt()`이면 `409`(`ErrorCode.ACTIVITY_RECORD_NOT_STARTED`)로 거부한다.
- `updateRecord`(수정)에는 이 검증을 적용하지 않는다 — 출석 체크와 동일한 논리로, 기록이 존재한다는 것 자체가 이미 시작 시각이 지났음을 보장한다.
- `ActivityService`에 `Clock clock` 필드를 새로 추가했다 (기존에는 시간 관련 로직이 없어 주입받지 않고 있었음). `AttendanceService`/`ScheduleService`가 쓰는 것과 같은 `scheduleClock` 빈을 그대로 사용한다.

### 출석 체크 시작 시각 검증

- `AttendanceService.checkAttendance`에서 `validateManager` 다음으로 `validateAttendanceStarted(scheduleId)`를 호출한다. `now < schedule.getScheduledAt()`이면 `409`(`ErrorCode.ATTENDANCE_CHECK_NOT_STARTED`)로 거부한다.
- `updateAttendance`(정정)에는 이 검증을 적용하지 않는다 — record가 존재한다는 것 자체가 이미 `checkAttendance`(또는 자동 처리)를 통해 시작 시각이 지났음을 보장하므로, 정정 시점에 다시 검증할 필요가 없다.

### 자동 결석 처리

- **2시간 판단은 DB 필드 추가 없이 계산한다.** `AttendanceService.AUTO_ABSENCE_WINDOW = Duration.ofHours(2)` 상수로 계산하고, 별도 컬럼을 두지 않는다.
- **실제 `AttendanceRecord` row를 만든다.** "저장하지 않고 조회 시점마다 가상으로 계산"하는 대안도 검토했으나, `getMyAttendanceRate`/`getGroupAttendanceRates`가 이미 저장된 row를 상태별로 `COUNT`하는 방식이라(`MyAttendanceRateResponse.java`) 가상 계산으로 가면 이 집계 로직을 전부 다시 짜야 해서 배제했다.
- **트리거는 두 가지를 병행한다.**
  1. **조회 시점 지연 처리**: `getSummary`/`getMyAttendanceRate`/`getGroupAttendanceRates` 호출 시 그 시점에 `autoProcessOverdueAttendance()`를 먼저 실행해 2시간 지난 미체크 인원을 채운 뒤 집계한다. 이 세 메서드는 클래스 기본 `@Transactional(readOnly = true)`를 오버라이드해 쓰기 가능한 `@Transactional`을 명시적으로 붙였다.
  2. **안전망 배치**: `AttendanceAutoProcessingScheduler`가 1시간마다(`@Scheduled(fixedRate = ...)`) 같은 메서드를 호출한다. 아무도 조회하지 않는 스케줄도 최악의 경우 2시간(판정 기준) + 1시간(배치 주기) 이내에는 반영된다. `MoiGoApplication`에 `@EnableScheduling`을 추가해야 동작한다.
  - 처리 대상 스케줄을 찾을 때 schedule 모듈에 새 쿼리 메서드를 추가하지 않고, attendance 모듈이 이미 주입받아 쓰고 있는 `StudyScheduleRepository`의 기존 `findAll()`을 그대로 사용해 애플리케이션 코드(Java 스트림)에서 `scheduledAt + 2h <= now` 필터링을 수행한다.
  - `(scheduleId, userId)` 유니크 제약 덕분에 이미 체크된(수동 또는 이전 자동 처리) 멤버는 자연히 스킵되므로, 지연 처리와 배치가 겹치거나 반복 실행돼도 안전하다(idempotent).
- **`checked_by`는 `null`을 허용한다.** 자동 처리는 사람이 체크한 게 아니기 때문.
  - FK 제약(`fk_attendance_records_checker`, `sql/moigo_schema_seed.sql:252-255`)은 그대로 둬도 된다 — MySQL/InnoDB는 참조 컬럼 값이 `NULL`이면 FK 검사 대상에서 제외하므로, FK를 유지한 채로 `NULL`을 허용해도 안전하다.
  - 적용된 변경:
    - `sql/moigo_schema_seed.sql:239` — `checked_by BIGINT NOT NULL` → `NULL`
    - `AttendanceRecord.java:33` — `@Column(name = "checked_by", nullable = false)` → `@Column(name = "checked_by")`(nullable)
    - `AttendanceRecordResponse.checkedBy`는 이미 `Long`(래퍼 타입)이라 `null`이 들어와도 그대로 JSON에 `null`로 나가며 별도 수정은 필요 없다(확인 완료).
