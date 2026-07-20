# Part3 일정 생성 설계

## 목적과 범위

활성 스터디 그룹의 `LEADER` 또는 `MANAGER`가 미래 일정을 생성하는 API를 구현한다. 일정은
생성 시 장소가 미정일 수 있으며, 저장된 일정 전체를 응답해 클라이언트가 별도 조회 없이 화면을
갱신할 수 있게 한다.

이번 작업 범위는 일정 JPA 영속성, 생성 요청·응답 DTO, 생성 Service와 Controller, 관련
계층별 테스트다. 일정 목록·상세 조회와 수정·삭제는 후속 단계에서 구현한다. 장소 미정 일정을
확정하는 동작도 일정 수정 API의 범위로 남긴다.

## API 계약

- 메서드와 경로: `POST /api/groups/{groupId}/schedules`
- 인증: `@AuthenticationPrincipal AuthenticatedMember` 필수, Part3는 `id()`만 사용
- 성공 상태: `201 Created`
- 성공 본문: `ApiResponse<ScheduleResponse>`
- 상세 요청·응답 필드와 오류 표는 [`api.md`](api.md)의 "일정 생성"을 기준으로 삼는다.

## 입력과 정규화

`ScheduleCreateRequest`는 다음 입력을 받는다.

| 필드 | 규칙 |
| --- | --- |
| `title` | 필수, 양끝 공백 제거 후 1~100자 |
| `scheduledAt` | 필수, 생성 시점보다 미래 |
| `location` | 선택, 양끝 공백 제거 후 최대 255자, 빈 값은 `null` |
| `onlineLink` | 선택, 양끝 공백 제거 후 최대 500자, 빈 값은 `null` |
| `content` | 선택, 양끝 공백 제거 후 최대 5,000자, 빈 값은 `null` |
| `materials` | 선택, 양끝 공백 제거 후 최대 5,000자, 빈 값은 `null` |
| `responseDeadline` | 선택, 현재보다 미래이고 `scheduledAt`보다 같거나 빨라야 함 |

문자열의 형식·길이 검증과 공백 정규화는 Request DTO 경계에서 수행한다. 현재 시각과 그룹
상태·역할에 의존하는 검증은 Service가 담당한다. `onlineLink`는 URL 형식을 강제하지 않고
온라인 접속 정보를 일반 문자열로 저장한다.

`location`과 `onlineLink`의 의미는 다음과 같다.

| `location` | `onlineLink` | 의미 |
| --- | --- | --- |
| `null` | `null` | 장소 미정 |
| 값 있음 | `null` | 오프라인 일정 |
| `null` | 값 있음 | 온라인 일정 |
| 값 있음 | 값 있음 | 온·오프라인 병행 일정 |

별도의 일정 유형 또는 장소 확정 상태 Enum은 추가하지 않는다.

## 계층과 책임

### Controller

`ScheduleController`는 경로 변수, `ScheduleCreateRequest`, 인증 사용자를 받아
`ScheduleService.create(groupId, memberId, request)`에 전달한다. 인증 principal이 없으면 기존
Part1 `UNAUTHORIZED` 계약을 사용한다. Controller는 권한·시간 검증이나 Repository 호출을
수행하지 않는다.

### Service

`ScheduleService`는 단일 생성 유스케이스의 트랜잭션 경계를 담당한다. `Clock`을 주입받아
`LocalDateTime.now(clock)`으로 한 번 계산한 현재 시각을 모든 시간 검증에 사용한다.

처리 순서는 다음과 같이 고정한다.

1. `StudyGroupRepository.findById(groupId)`로 그룹 존재 확인
2. `GroupMemberRepository.findByStudyGroupIdAndUserId(groupId, memberId)`로 소속 확인
3. 그룹원의 `WITHDRAWN` 상태 확인
4. 그룹의 `ENDED` 상태 확인
5. 그룹원의 `LEADER` 또는 `MANAGER` 역할 확인
6. `scheduledAt`과 `responseDeadline` 시간 규칙 확인
7. `StudySchedule` 생성 및 `StudyScheduleRepository.save` 호출
8. 저장 결과를 `ScheduleResponse`로 변환

한 트랜잭션 안에서 검증과 저장을 수행한다. Part1 회원 Repository와 인증 내부 구현은 사용하지
않는다.

### Entity

`StudySchedule`은 `study_schedules` 스키마와 일치하는 JPA Entity다. Part3가 소유하는
`StudyGroup`만 지연 로딩 연관관계로 매핑하고, 외부 Part1 사용자는 `creatorId` 값으로만
저장한다.

Entity는 공개 setter를 제공하지 않고 생성 팩터리를 사용한다. Entity가 항상 지켜야 하는
`responseDeadline <= scheduledAt` 관계를 생성 시 검증한다. "현재보다 미래" 규칙은 생성
유스케이스 시점에만 필요한 정책이므로 Service에서 검증한다.

`createdAt`과 `updatedAt`은 최초 저장 시 같은 시각으로 초기화한다. 이후 수정 API에서
`updatedAt` 갱신 방식을 구현한다.

### Repository

`StudyScheduleRepository`는 `JpaRepository<StudySchedule, Long>`를 상속한다. 생성 작업에서는
저장만 사용하지만, ERD의 `(group_id, scheduled_at)` 인덱스 계약과 후속 조회 기능을 고려해
그룹별 일정 시간순 조회 메서드도 함께 선언한다.

### Response DTO

`ScheduleResponse`는 Entity를 노출하지 않고 다음 필드를 반환한다.

- `scheduleId`, `groupId`, `creatorId`
- `title`, `scheduledAt`
- `location`, `onlineLink`, `content`, `materials`
- `responseDeadline`, `createdAt`, `updatedAt`

## 권한과 오류

| 검증 실패 | 오류 코드 | HTTP 상태 |
| --- | --- | --- |
| 그룹 없음 | `GROUP_NOT_FOUND` | 404 |
| 그룹원 기록 없음 | `GROUP_ACCESS_DENIED` | 403 |
| 탈퇴 그룹원 | `WITHDRAWN_GROUP_MEMBER` | 403 |
| 종료 그룹 | `GROUP_ENDED` | 409 |
| 활성 `MEMBER` | `SCHEDULE_MANAGEMENT_FORBIDDEN` | 403 |
| 일정·마감 시간 오류 | `INVALID_SCHEDULE_TIME` | 400 |
| DTO 형식·길이 오류 | `INVALID_REQUEST` | 400 |

이번 구현에서는 합의된 `GROUP_ENDED`, `SCHEDULE_MANAGEMENT_FORBIDDEN`,
`INVALID_SCHEDULE_TIME`만 `global/exception/ErrorCode.java`에 추가한다. 기존 `ApiResponse`,
`GlobalExceptionHandler`, 보안 설정과 다른 파트 코드는 수정하지 않는다.

## 시간 규칙

Service가 `Clock`에서 얻은 현재 시각을 `now`라고 정의한다.

```text
scheduledAt > now

responseDeadline == null
또는
now < responseDeadline <= scheduledAt
```

`responseDeadline == scheduledAt`은 허용한다. 시간 비교는 하나의 요청에서 동일한 `now`를
사용해 경계값 판정이 호출 도중 달라지지 않게 한다.

## 테스트 설계

### Request DTO 테스트

- 유효한 모든 입력과 최소 입력을 생성한다.
- 제목의 양끝 공백을 제거한다.
- 선택 문자열의 양끝 공백을 제거하고 빈 값을 `null`로 바꾼다.
- 제목 필수·100자, 장소 255자, 온라인 접속 정보 500자, 내용·준비물 5,000자 경계를 검증한다.
- `scheduledAt` 필수 검증을 확인한다.

### Entity 테스트

- 모든 필드를 가진 일정과 장소 미정 일정을 생성한다.
- `responseDeadline == scheduledAt`을 허용한다.
- `responseDeadline > scheduledAt`을 거절한다.
- 생성 시각과 수정 시각을 최초 저장 시 초기화한다.

### Repository 테스트

- 스키마의 컬럼 길이, NULL 허용과 연관관계 매핑을 확인한다.
- 그룹과 생성자 식별자를 저장하고 다시 조회한다.
- 그룹별 일정이 `scheduledAt` 오름차순으로 조회되는지 확인한다.

### Service 테스트

- 활성 `LEADER`와 `MANAGER`의 일정 생성을 허용한다.
- 장소와 링크가 모두 없는 일정을 허용한다.
- 그룹 없음, 비회원, 탈퇴 그룹원, 종료 그룹, 활성 `MEMBER`를 각각 합의된 오류로 거절한다.
- 현재와 같은 시각 또는 과거의 `scheduledAt`을 거절한다.
- 미래 일정과 유효한 미래 마감을 허용한다.
- 과거·현재 마감 및 일정보다 늦은 마감을 거절한다.
- 마감과 일정 시각이 같으면 허용한다.
- 저장 결과의 모든 필드를 응답으로 변환한다.

### Controller 테스트

- `POST /api/groups/{groupId}/schedules` 경로와 `201 Created`를 확인한다.
- 인증 principal의 `id()`가 Service로 전달되는지 확인한다.
- 성공 응답의 전체 JSON 계약을 확인한다.
- 요청 필수값과 길이 검증 실패가 `400 Bad Request`인지 확인한다.
- 비즈니스 예외의 HTTP 상태와 메시지를 확인한다.

### 최종 검증

```powershell
.\gradlew.bat test
.\gradlew.bat spotlessCheck
```

관련 테스트 이후 전체 테스트와 Spotless 검사를 실행하고, Part3 및 합의된 `ErrorCode` 외의
코드가 변경되지 않았는지 확인한다.
