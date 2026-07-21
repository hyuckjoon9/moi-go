# Part4 출석 · Activity 외부 도메인 협의사항

> 이 문서는 `attendance`, `activity` 도메인이 소유하지 않는 `study_schedules`(Part3)와 맺는 FK·삭제 정책 협의 내용을 기록한다. Part3가 일정 삭제 API를 구현하기 전에 이 문서를 기준으로 정책을 맞춘다.

## 일정 삭제 정책

- 미래 일정만 삭제할 수 있다.
- 과거 일정은 삭제를 금지한다.
- 참석 응답(`attendance_responses`)만 존재하면 일정과 함께 삭제한다.
- 출석 기록(`attendance_records`)이나 활동 기록(`activity_records`)이 존재하면 일정 삭제를 거부한다.

## FK 삭제 정책

`study_schedules.id`를 참조하는 세 테이블의 `ON DELETE` 정책은 다음과 같이 합의했다.

| 테이블 | 합의된 정책 | 현재 DB(`moigo`) | 상태 |
| --- | --- | --- | --- |
| `attendance_responses` | `CASCADE` | `CASCADE` | 일치 |
| `attendance_records` | `RESTRICT` | `RESTRICT` | 일치 (2026-07-21 마이그레이션 반영) |
| `activity_records` | `RESTRICT` | `RESTRICT` | 일치 (2026-07-21 마이그레이션 반영) |

- 참석 응답은 일정 삭제 시 자동으로 함께 사라져도 되는 데이터라 `CASCADE`로 유지한다.
- 출석 기록과 활동 기록은 실제 활동 이력이므로, 일정이 삭제되어도 사라지면 안 된다는 합의에 따라 `RESTRICT`로 정했다.
- 이 `RESTRICT` 합의는 최초 스키마 설계보다 나중에 확정됐고, 2026-07-21에 `moigo` 운영 DB에 마이그레이션이 반영되어 현재는 문서와 DB가 일치한다(`SHOW CREATE TABLE`로 확인).

## 참석 응답 마감(`response_deadline`) 정책

- 마감 후에는 응답 등록·변경·삭제를 모두 금지한다.
- 마감을 단축·연장해도 기존 응답을 삭제하거나 무효화하지 않는다.
- 기존 마감이 지난 뒤에는 재개방을 금지한다.
- `response_deadline == null`이면 `scheduled_at`을 마감으로 사용한다.

## 해결된 이슈

- [x] `attendance_records`, `activity_records`의 `schedule_id` FK를 `CASCADE`에서 `RESTRICT`로 바꾸는 DB 마이그레이션을 2026-07-21에 반영했다.

DB가 이제 `RESTRICT`이므로, 일정 삭제 API(Part3)는 출석·활동 기록이 남아있는 일정을 삭제하면 DB 제약 위반으로 실패한다. 다만 이 예외를 사용자에게 그대로 노출하지 않도록, Part3 서비스 계층에서 삭제 전에 출석 기록·활동 기록 존재 여부를 먼저 조회해 이 문서의 정책에 맞는 명확한 오류(예: `ErrorCode`)로 변환해 응답하는 것을 권장한다.
