# 일정 조회 API 설계

## 목적

그룹의 활성 구성원이 해당 그룹에서 생성된 모든 일정에 접근할 수 있도록 예정 일정 목록,
지난 일정 목록과 일정 상세 조회 API를 제공한다. 일정이 계속 누적되어도 응답 크기와 정렬이
안정적이도록 목록은 범위별 페이지 조회로 구현한다.

## 범위

이번 작업에 포함하는 기능은 다음과 같다.

- 예정 일정 페이지 조회
- 지난 일정 페이지 조회
- 일정 상세 조회
- 그룹 접근 권한 검증
- 목록과 상세 응답 DTO
- Repository, Service, Controller 테스트
- `docs/part3-group/api.md` 계약 갱신

일정 수정·삭제, 참석 응답과 출석·활동 데이터 조립은 이번 작업에 포함하지 않는다.

## API 계약

### 일정 목록

```http
GET /api/groups/{groupId}/schedules?scope=upcoming&page=0&size=20
```

| 입력 | 위치 | 기본값 | 규칙 |
| --- | --- | --- | --- |
| `groupId` | Path | 없음 | 조회할 그룹 식별자 |
| `scope` | Query | `upcoming` | `upcoming` 또는 `past` |
| `page` | Query | `0` | 0 이상 |
| `size` | Query | `20` | 1 이상 100 이하 |
| 인증 사용자 | Principal | 없음 | `AuthenticatedMember.id()`만 사용 |

`scope=upcoming`은 서비스가 조회를 시작할 때 얻은 기준 시각과 같거나 이후인 일정을
`scheduledAt`, `id` 오름차순으로 반환한다. `scope=past`는 기준 시각보다 이전인 일정을
`scheduledAt`, `id` 내림차순으로 반환한다. 두 범위가 서로 겹치거나 누락되지 않으므로 두
목록을 통해 그룹의 모든 일정에 접근할 수 있다.

목록 항목은 화면에 필요한 요약 필드만 반환한다.

- `scheduleId`
- `creatorId`
- `title`
- `scheduledAt`
- `location`
- `onlineLink`
- `responseDeadline`

페이지 응답은 `items`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`를 포함한다.
`content`, `materials`, `createdAt`, `updatedAt`은 상세 응답에서 제공한다.

### 일정 상세

```http
GET /api/groups/{groupId}/schedules/{scheduleId}
```

과거와 예정 일정을 구분하지 않고 조회한다. 성공 응답은 일정 생성 API에서 사용하는
`ScheduleResponse`를 재사용하여 저장된 전체 필드를 반환한다. Repository 조회 조건에
`groupId`와 `scheduleId`를 함께 사용하여 다른 그룹의 일정이 노출되지 않게 한다.

## 접근 권한과 검증 순서

목록과 상세 조회는 그룹 역할과 관계없이 `ACTIVE` 그룹원에게 허용한다. 읽기 기능이므로
`ENDED` 그룹도 활성 그룹원이 조회할 수 있으며, 이는 그룹 홈 조회 규칙과 일치한다.

검증 순서는 다음과 같이 고정한다.

1. 그룹 존재 여부
2. 인증 사용자의 그룹원 기록 존재 여부
3. 그룹원의 탈퇴 상태
4. 상세 조회인 경우 일정 존재 여부와 그룹 소속

비회원에게 일정 존재 여부가 먼저 노출되지 않도록 그룹 접근 검증을 일정 조회보다 먼저 한다.

## 구성 요소와 데이터 흐름

### Controller

`ScheduleController`에 목록과 상세 `GET` 매핑을 추가한다. Controller는 요청 파라미터 검증,
인증 사용자 ID 전달, 서비스 호출과 `ApiResponse` 조립만 담당한다. 인증 Principal이 없으면
기존 생성 API와 동일하게 `UNAUTHORIZED`를 발생시킨다.

### DTO

- `ScheduleScope`: `UPCOMING`, `PAST` 범위를 표현하고 요청의 소문자 값을 처리한다.
- `ScheduleSummaryResponse`: 목록용 요약 필드를 표현한다.
- `SchedulePageResponse`: 일정 요약 목록과 명시적인 페이지 메타데이터를 표현한다.
- `ScheduleResponse`: 상세 조회에서 기존 전체 응답을 재사용한다.

Spring Data의 `Page` 구현을 API에 직접 노출하지 않는다. 이를 통해 내부 페이지 구현과 외부
응답 계약을 분리한다.

### Service

`ScheduleService`에 읽기 전용 목록·상세 메서드를 추가한다. 기존 그룹과 그룹원 Repository를
사용해 접근 권한을 검증하고, 주입된 `scheduleClock`에서 목록 조회 기준 시각을 한 번만 얻는다.
그 기준 시각을 Repository에 전달하여 페이지 경계 안에서 일관된 시간 조건을 사용한다.

### Repository

`StudyScheduleRepository`는 다음 조회를 제공한다.

- 그룹 ID와 기준 시각으로 예정 일정 페이지 조회
- 그룹 ID와 기준 시각으로 지난 일정 페이지 조회
- 그룹 ID와 일정 ID로 상세 조회

예정 일정은 `(scheduledAt ASC, id ASC)`, 지난 일정은 `(scheduledAt DESC, id DESC)`로
정렬한다. `id` 보조 정렬은 같은 시각의 일정이 여러 개일 때 페이지 결과가 흔들리지 않도록
한다. 기존 `(group_id, scheduled_at)` 인덱스의 선두 컬럼과 시간 범위 조건을 활용한다.

## 오류 처리

| 상황 | HTTP 상태 | 오류 코드 | 메시지 |
| --- | --- | --- | --- |
| 그룹이 존재하지 않음 | `404 Not Found` | `GROUP_NOT_FOUND` | `그룹을 찾을 수 없습니다.` |
| 그룹원 기록이 없음 | `403 Forbidden` | `GROUP_ACCESS_DENIED` | `그룹에 접근할 권한이 없습니다.` |
| 탈퇴한 그룹원 | `403 Forbidden` | `WITHDRAWN_GROUP_MEMBER` | `탈퇴한 그룹원은 그룹에 접근할 수 없습니다.` |
| 일정이 없거나 다른 그룹 소속 | `404 Not Found` | `SCHEDULE_NOT_FOUND` | `일정을 찾을 수 없습니다.` |
| 잘못된 범위·페이지 요청 | `400 Bad Request` | `INVALID_REQUEST` | 공통 요청 검증 메시지를 따름 |
| 인증 정보 없음 | `401 Unauthorized` | `UNAUTHORIZED` | `인증이 필요합니다.` |

이번 작업에서 공통 영역 변경은 `ErrorCode`에 `SCHEDULE_NOT_FOUND`를 추가하는 것으로
제한한다.

## 테스트 전략

### Repository 테스트

- 기준 시각과 같은 일정이 예정 목록에 포함되는지 검증한다.
- 기준 시각 이전 일정만 지난 목록에 포함되는지 검증한다.
- 예정·지난 일정의 주 정렬과 동일 시각 `id` 보조 정렬을 검증한다.
- 다른 그룹의 일정이 제외되는지 검증한다.
- 그룹 ID와 일정 ID를 함께 사용하는 상세 조회를 검증한다.

### Service 테스트

- 활성 `LEADER`, `MANAGER`, `MEMBER`가 목록과 상세를 조회할 수 있는지 검증한다.
- 종료된 그룹의 활성 그룹원이 조회할 수 있는지 검증한다.
- 없는 그룹, 비회원과 탈퇴 그룹원의 오류 및 검증 순서를 확인한다.
- 없는 일정과 다른 그룹 일정이 `SCHEDULE_NOT_FOUND`인지 확인한다.
- 고정 `Clock`을 사용해 예정·지난 일정의 경계 시각을 검증한다.
- 빈 페이지와 페이지 메타데이터 변환을 검증한다.

### Controller 테스트

- 목록과 상세 경로, 성공 상태와 `ApiResponse` 구조를 검증한다.
- `scope` 생략 시 `upcoming`, 페이지 생략 시 `page=0`, `size=20`이 적용되는지 확인한다.
- `past` 요청과 페이지 메타데이터를 확인한다.
- 지원하지 않는 `scope`, 음수 `page`, 0 또는 100 초과 `size`를 거부하는지 검증한다.
- 인증 Principal 누락과 비즈니스 예외의 HTTP 매핑을 검증한다.

## 완료 조건

- 활성 그룹원이 예정·지난 목록을 통해 그룹의 모든 일정을 페이지 단위로 조회할 수 있다.
- 활성 그룹원이 과거·예정 일정의 상세 내용을 조회할 수 있다.
- 비회원, 탈퇴 그룹원과 다른 그룹 일정 접근이 계약대로 차단된다.
- API 문서와 구현이 일치한다.
- 관련 테스트, 전체 테스트와 `spotlessCheck`가 통과한다.
