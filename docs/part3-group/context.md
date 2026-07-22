# Part3 작업 컨텍스트

> 기준: 2026-07-22, `develop`의 `db9161c`
>
> 실제 Git 상태가 이 문서와 다르면 저장소를 우선한다. 개인 기록인 `.local/part3/updates.md`가 있으면 최신 항목만 참고하되 오래된 브랜치 기록은 무시한다.

## 세션 시작

1. 루트 [`AGENTS.md`](../../AGENTS.md)와 [`development-guide.md`](development-guide.md)를 읽는다.
2. 현재 브랜치·변경·최근 커밋을 확인한다.
3. 작업과 관련된 [`api.md`](api.md), [`erd.md`](erd.md), 기능 설계 문서를 읽는다.
4. 아래 "바로 다음 작업"부터 진행한다.

```powershell
git branch --show-current
git status --short
git log -3 --oneline
```

## 범위와 현재 상태

- Part3 소유 패키지: `study`, `schedule`
- Part3 소유 테이블: `study_groups`, `group_members`, `study_schedules`
- 외부 경계: Part1 `auth/member`, Part2 `recruitment/application`, Part4 `attendance`, `activity`, `global`
- 그룹 영속성·생성·홈 조회와 일정 생성·조회·수정·응답 마감 변경·삭제가 `develop`에 반영됐다.
- 일정 삭제는 참석 응답 `CASCADE`, 출석·활동 기록 `RESTRICT` 및 서비스 사전 검사로 보호한다.
- Part3는 `ScheduleAttendancePolicyReader`를 제공하지만 Part4 참석 응답 서비스는 아직 이를 사용하지 않는다.
- Part3는 `StudyGroupAttendanceRatePolicyReader`를 제공한다. 활성 그룹의 활성 `LEADER` 또는 `MANAGER`만 `canViewAllAttendanceRates=true`를 받으며, Part4는 이 결과로 그룹 전체 출석률 조회를 제한한다.

## 구현 완료 계약

### 그룹

- `postId`를 멱등 키로 그룹을 한 번만 생성하고 모집장은 `LEADER`, 승인 회원은 `MEMBER`로 등록한다.
- `GET /api/groups/me`는 활성 그룹원이 속한 `ACTIVE`, `ENDED` 그룹을 가입 시각 내림차순으로 반환한다. 프론트엔드는 역할로 운영·참여 그룹을, 그룹 상태로 진행·종료 이력을 구분한다.
- 그룹 홈 `GET /api/groups/{groupId}`는 활성 그룹원에게 그룹과 활성 그룹원 목록을 반환한다.
- 종료 그룹도 조회할 수 있다. 구성원은 역할, 가입 시각, 사용자 ID 순으로 정렬한다.

### 일정

- 활성 그룹의 활성 `LEADER`·`MANAGER`만 생성·수정·마감 변경·삭제할 수 있다.
- 활성 그룹원은 역할과 무관하게 종료 그룹을 포함한 일정 목록·상세를 조회할 수 있다.
- 생성은 미래 일정과 유효한 응답 마감만 허용한다.
- 수정은 `PUT` 전체 교체이며 등록자·응답 마감·생성 시각을 보존한다.
- 응답 마감은 전용 `PATCH`로 설정·변경·제거한다. 기존 유효 마감 이후 재개방하지 않는다.
- 미래 일정만 삭제하며 출석·활동 기록이 있으면 `SCHEDULE_DELETE_NOT_ALLOWED`로 거부한다.

상세 필드·오류는 `api.md`, 데이터 제약은 `erd.md`, 설계 이유는 해당 `*-design.md`를 기준으로 한다.

## 바로 다음 작업

Part2가 `StudyGroupProvisioningPort`를 게시·신청 승인·스터디 취소 흐름에 연결하고 기존 `confirmGroup()`을 대체한다. 세 호출 지점은 부분 전환하지 않고 같은 변경에서 전환하며, Part3 예외가 Part2 상태 변경까지 롤백하는 통합 테스트를 추가한다.

이후 작업:

Part4 참석 응답 등록·변경·삭제에 Part3 일정 정책을 적용한다.

1. `AttendanceService`가 `ScheduleAttendancePolicyReader`를 통해 일정·그룹원·실질 마감을 확인하게 한다.
2. 세 동작 모두 `now < effectiveDeadline`일 때만 허용하고, 마감 시 `ATTENDANCE_RESPONSE_CLOSED`를 반환한다.
3. `schedule-fk-verification.sql`과 통합 테스트로 `CASCADE`·`RESTRICT` 계약을 확인한다.
4. Part3·Part4·활동 관련 테스트, 전체 테스트와 `spotlessCheck`를 실행한다.

이 작업은 Part4 소유 코드를 수정하므로 `development-guide.md`의 협업 원칙에 따라 담당자 리뷰와 변경 이유를 PR에 명시한다.

Part4 출석률 조회는 개인 조회를 인증 사용자 본인으로 제한하고, 그룹 전체 조회는 `groupId` 범위의 집계와 `StudyGroupAttendanceRatePolicyReader` 권한 확인을 함께 적용한다.

## 세션 종료 시 갱신

- 기준 날짜와 `develop` HEAD
- 구현 완료 상태에서 달라진 항목
- 새 결정 또는 blocker
- 바로 다음 작업
- 보호해야 할 미커밋 파일

과거 브랜치·커밋·PR의 상세 일지는 Git 이력으로 확인하고 이 문서에 누적하지 않는다.
