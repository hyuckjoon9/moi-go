# 출석 체크 시간 정책 & 자동 결석 처리 설계

작성일: 2026-07-23

`docs/part4-attendance/qa-fixes.md`에서 나온 "출석 체크에 시간 제약이 없다", "회고 작성이 스케줄 시작 전에도 가능하다" 항목을 어떻게 풀지 논의하고 정리한 결과.

## 배경

- 참여 응답(RSVP)은 `ScheduleAttendancePolicy.effectiveDeadline()`으로 마감 이후 응답을 막고 있는데(`AttendanceService.validateResponseOpen`), 출석 체크(`checkAttendance`/`updateAttendance`)와 회고 작성(`ActivityService.createRecord`/`updateRecord`)은 역할 검증(`validateManager`)만 하고 시간 제약이 전혀 없다.
- 회고는 스케줄 시작 이후라면 아무 때나 작성해도 문제없다 (내용 작성이라 늦게 써도 데이터 신뢰성 문제 없음).
- 출석 체크는 반대로, 시간이 한참 지난 뒤에도 상태를 새로 만들거나 바꿀 수 있다는 게 데이터 신뢰성 문제로 이어질 수 있어 별도 정책이 필요하다.

## 결론 (정책)

1. **회고 작성**: 스케줄 시작(`scheduledAt`) 이후로는 제한 없이 허용한다. `createRecord`/`updateRecord`에 `now >= scheduledAt` 검증만 추가하면 된다.
2. **출석 체크**: 스케줄 시작 전에는 불가능하다.
3. **자동 결석 처리**: 시작 후 2시간이 지나도 체크되지 않은 그룹원은 시스템이 자동으로 상태를 채운다.
   - 사전에 참여 응답(RSVP)에서 `ABSENT`(불참)로 응답한 사람 → 자동으로 `EXCUSED`(사유 결석)
   - `ATTEND`/`UNDECIDED`/무응답인 사람 → 자동으로 `ABSENT`(무단 결석)
4. 자동 처리 이후에도 모집장은 `updateAttendance`로 언제든 정정할 수 있다 — 별도 시간 제한을 두지 않는다. (정상적으로 참석을 체크했다면 2시간 안에 끝났을 일이라 정정이 필요한 경우는 드물지만, 안전장치로 열어둔다.)

## 구현 방식

- **2시간 판단은 DB 필드 추가 없이 계산한다.** `schedule.getScheduledAt().plusHours(2)`처럼 코드(상수 또는 설정값)로 계산하고, 별도 컬럼을 두지 않는다.
- **자동 처리는 실제 `AttendanceRecord` row를 만드는 배치(`@Scheduled`) 방식으로 한다.** "저장하지 않고 조회 시점마다 가상으로 계산"하는 대안도 검토했으나, `getMyAttendanceRate`/`getGroupAttendanceRates`가 이미 저장된 row를 상태별로 `COUNT`하는 방식이라(`MyAttendanceRateResponse.java`) 가상 계산으로 가면 이 집계 로직을 전부 다시 짜야 해서 배제했다. 배치로 실제 row를 만들면 기존 집계 로직을 그대로 재사용할 수 있다.
- **`checked_by`는 `null`을 허용해야 한다.** 자동 처리는 사람이 체크한 게 아니기 때문.
  - FK 제약(`fk_attendance_records_checker`, `sql/moigo_schema_seed.sql:252-255`)은 그대로 둬도 된다 — MySQL/InnoDB는 참조 컬럼 값이 `NULL`이면 FK 검사 대상에서 제외하므로, FK를 유지한 채로 `NULL`을 허용해도 안전하다.
  - 필요한 변경:
    - `sql/moigo_schema_seed.sql:239` — `checked_by BIGINT NOT NULL` → `NULL`
    - `AttendanceRecord.java:33` — `@Column(name = "checked_by", nullable = false)` → `nullable = true`
    - `AttendanceRecordResponse.checkedBy`는 이미 `Long`(래퍼 타입)이라 `null`이 들어와도 그대로 JSON에 `null`로 나가며 별도 수정은 필요 없다(확인 완료).

- 자동 처리 대상 판단 시 탈퇴(`WITHDRAWN`) 그룹원은 제외할지 여부 (참여 응답/출석 체크의 기존 활성 그룹원 검증과 일관되게 활성 그룹원만 대상으로 하는 게 자연스러워 보임)
