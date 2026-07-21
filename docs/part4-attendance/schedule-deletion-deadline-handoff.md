# Part4 전달: 일정 삭제·응답 마감 정책 계약

> 작성일: 2026-07-21
>
> 상태: Part3 공유 계약 추가 완료. 이 파일은 로컬 전달 기록이며 Git에 커밋하지 않는다.

## Part3에서 추가한 계약

- `ScheduleAttendancePolicyReader#getAttendancePolicy(Long scheduleId, Long userId)`
- `ScheduleAttendancePolicy` 값 객체
  - `scheduleId`, `groupId`, `groupStatus`, `activeGroupMember`, `scheduledAt`, `responseDeadline`
  - `effectiveDeadline()`은 명시적 `responseDeadline`이 없으면 `scheduledAt`을 반환한다.
- 일정 삭제 사전 검사용 `AttendanceRecordLookup#existsByScheduleId(Long scheduleId)`
- 공통 오류 코드 `ATTENDANCE_RESPONSE_CLOSED` (`409 Conflict`)

## Part4 적용 요청

참석 응답의 등록·변경·삭제 직전에 위 정책 리더를 호출해 다음을 보장한다.

1. 일정이 존재하고 요청 사용자가 활성 그룹원인지 확인한다.
2. 그룹이 `ACTIVE`인지 확인한다.
3. `now < policy.effectiveDeadline()`일 때만 응답을 허용한다.
4. `now == effectiveDeadline` 및 이후 요청은 모두 `ATTENDANCE_RESPONSE_CLOSED`로 거부한다.

Part4는 Part3 Repository를 직접 의존하지 않고 위 포트만 사용한다. 일정 삭제가 참석 응답만
포함한 경우 응답은 FK `ON DELETE CASCADE`로 함께 삭제되므로, 응답 존재 여부는 삭제 차단 조건이 아니다.
출석 기록 존재 여부만 `AttendanceRecordLookup`으로 Part3에 제공한다.

## Part4 검증 범위

- 명시 마감과 `scheduledAt` 대체 마감 모두에서, 마감 직전은 허용하고 동일 시각·이후는 거부
- 등록·변경·삭제 세 동작에 동일한 마감 정책 적용
- 비회원·탈퇴 회원·종료 그룹의 응답 거부
- 기존 응답은 마감 변경 후에도 소급 삭제하거나 무효화하지 않음

## 관련 Part3 파일

- `src/main/java/com/mycom/myapp/schedule/service/ScheduleAttendancePolicy.java`
- `src/main/java/com/mycom/myapp/schedule/service/port/ScheduleAttendancePolicyReader.java`
- `src/main/java/com/mycom/myapp/schedule/service/port/AttendanceRecordLookup.java`
- `src/main/java/com/mycom/myapp/global/exception/ErrorCode.java`

구현 계획 전체: `docs/part3-group/schedule-deletion-deadline-implementation-plan.md`
