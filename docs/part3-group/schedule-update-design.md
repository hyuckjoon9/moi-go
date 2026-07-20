# 일정 수정 API 설계

## 문서 목적

이 문서는 Part3 일정 수정 API의 범위, 권한, 검증 순서와 계층별 책임을 정의한다. 구현은
이 문서와 [`api.md`](api.md), [`development-guide.md`](development-guide.md)의 계약을 함께
따른다.

## 작업 범위

이번 작업은 미래 일정의 수정만 제공한다.

- `PUT /api/groups/{groupId}/schedules/{scheduleId}`를 추가한다.
- 수정 가능한 전체 필드를 한 번에 교체한다.
- 활성 `LEADER`와 `MANAGER`에게만 수정 권한을 부여한다.
- 수정 완료 후 전체 `ScheduleResponse`를 반환한다.
- 일정 삭제는 Part4·활동 담당자와 연쇄 삭제 정책을 합의할 때까지 보류한다.
- `responseDeadline` 수정은 Part4의 참석 응답 정책을 합의할 때까지 보류한다.

## 선택한 API 방식

### `PUT` 전체 교체

현재 일정 편집은 상세 정보를 불러온 뒤 전체 편집 폼을 저장하는 유스케이스로 정의한다.
수정 대상 필드가 적고 생성 요청의 형식·길이 검증 규칙을 재사용할 수 있으므로 `PUT`을
사용한다.

```http
PUT /api/groups/{groupId}/schedules/{scheduleId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "title": "수정된 스터디 일정",
  "scheduledAt": "2026-07-28T19:00:00",
  "location": "강남 스터디룸",
  "onlineLink": null,
  "content": "4장 문제 풀이",
  "materials": null
}
```

`title`과 `scheduledAt`은 필수다. `location`, `onlineLink`, `content`, `materials`는
선택값이지만 전체 교체 대상이므로 요청에 없는 값도 `null`로 취급한다. 클라이언트는 기존 값을
유지하려면 그 값을 다시 전송해야 한다. 선택 문자열은 양끝 공백을 제거하고 빈 문자열이면
`null`로 정규화한다.

성공 상태는 `200 OK`이고 응답은 기존 `ApiResponse<ScheduleResponse>`를 사용한다.

### `PATCH`를 선택하지 않은 이유

부분 수정은 필드 누락과 명시적 `null`을 구분해야 한다. 현재 프로젝트에는 이를 표현하는 공통
DTO 타입이나 JSON Merge Patch 처리 방식이 없다. 선택값 제거를 정확하게 지원하면서 불필요한
역직렬화 계층이나 의존성을 도입하지 않기 위해 이번 작업에서는 `PUT`을 사용한다.

향후 부분 수정 요구가 생기면 `PATCH`를 별도 추가하거나 계약을 전환할 수 있다. Service와
Entity의 변경 로직은 HTTP 메서드에 의존하지 않도록 유지한다.

## 수정 필드

### 변경 대상

- `title`
- `scheduledAt`
- `location`
- `onlineLink`
- `content`
- `materials`
- `updatedAt`

### 유지 대상

- `id`
- `studyGroup`
- `creatorId`
- `responseDeadline`
- `createdAt`

`responseDeadline`은 Part3가 저장하는 일정 필드지만 참석 응답 허용 여부는 Part4가 소유한다.
마감 연장·단축·제거가 기존 참석 응답에 미치는 정책이 합의되지 않았으므로 이번 요청 DTO에는
포함하지 않는다.

## 권한과 상태 규칙

- 인증 사용자의 그룹원 상태가 `ACTIVE`여야 한다.
- 활성 그룹원 중 `LEADER`와 `MANAGER`만 일정을 수정할 수 있다.
- 활성 `MEMBER`는 일정 조회만 가능하고 수정할 수 없다.
- 일정 등록자 여부는 수정 권한에 영향을 주지 않는다.
- `WITHDRAWN` 그룹원은 역할과 관계없이 수정할 수 없다.
- `ENDED` 그룹은 일정 조회만 허용하고 수정할 수 없다.
- 기존 `scheduledAt`이 수정 기준 시각과 같거나 이전이면 이미 시작된 일정으로 보고 수정할 수
  없다.

## 시간 규칙

Service는 `Clock`에서 현재 시각을 한 번만 구해 모든 시간 검증에 같은 `now`를 사용한다.

- 기존 일정은 `scheduledAt > now`여야 한다.
- 요청의 새 `scheduledAt`도 `scheduledAt > now`여야 한다.
- 기존 `responseDeadline`이 있으면 새 `scheduledAt`보다 같거나 빨라야 한다.
- 이미 지난 `responseDeadline`은 값을 유지하는 조건으로 허용한다.
- `responseDeadline` 자체를 변경하거나 제거하지 않는다.

기존 일정이 이미 시작된 상태는 요청 값의 형식 오류가 아니라 현재 리소스 상태와 수정 동작의
충돌이므로 `SCHEDULE_UPDATE_NOT_ALLOWED`로 처리한다. 새 `scheduledAt`이 현재 이하이거나 기존
응답 마감보다 이르면 `INVALID_SCHEDULE_TIME`으로 처리한다.

## 검증 순서와 오류

Service는 다음 순서로 검증한다. 앞 단계가 실패하면 뒤 단계 Repository 조회나 검증을 수행하지
않는다.

1. 그룹 존재: `GROUP_NOT_FOUND` (`404 Not Found`)
2. 그룹원 기록 존재: `GROUP_ACCESS_DENIED` (`403 Forbidden`)
3. 그룹원 활동 상태: `WITHDRAWN_GROUP_MEMBER` (`403 Forbidden`)
4. 그룹 상태: `GROUP_ENDED` (`409 Conflict`)
5. 그룹 역할: `SCHEDULE_MANAGEMENT_FORBIDDEN` (`403 Forbidden`)
6. 일정 존재와 그룹 소속: `SCHEDULE_NOT_FOUND` (`404 Not Found`)
7. 기존 일정 시작 여부: `SCHEDULE_UPDATE_NOT_ALLOWED` (`409 Conflict`)
8. 요청 일정 시간: `INVALID_SCHEDULE_TIME` (`400 Bad Request`)
9. 기존 응답 마감과 새 일정 시간 관계: `INVALID_SCHEDULE_TIME` (`400 Bad Request`)

`SCHEDULE_UPDATE_NOT_ALLOWED`는 이번 기능에서 공통 `ErrorCode`에 추가한다. 기존 공통 응답과
예외 처리 구조는 변경하지 않는다.

## 계층별 설계

### Controller

`ScheduleController`에 `@PutMapping("/{scheduleId}")`을 추가한다. Controller는 인증 사용자
식별자를 확인하고 `@Valid ScheduleUpdateRequest`와 경로 식별자를 Service에 전달한다. 비즈니스
검증이나 Repository 호출은 수행하지 않는다.

### Request DTO

`ScheduleUpdateRequest`는 다음 필드를 제공한다.

```java
public record ScheduleUpdateRequest(
        String title,
        LocalDateTime scheduledAt,
        String location,
        String onlineLink,
        String content,
        String materials) {}
```

생성 요청과 동일한 문자열 길이 및 필수값 규칙을 적용한다.

- `title`: 정규화 후 필수, 최대 100자
- `scheduledAt`: 필수
- `location`: 최대 255자
- `onlineLink`: 최대 500자
- `content`: 최대 5,000자
- `materials`: 최대 5,000자

DTO는 문자열 형식과 길이만 검증한다. 현재 시각과 연관된 검증은 Service가 담당한다.

### Service

`ScheduleService.update(groupId, memberId, scheduleId, request)`는 쓰기 트랜잭션에서 권한과
시간을 검증하고 Entity 변경 메서드를 호출한다. 일정 조회에는 기존
`findByIdAndStudyGroupId(scheduleId, groupId)`를 사용한다. Part4·활동 Repository나 내부
구현에는 의존하지 않는다.

### Entity

`StudySchedule`은 공개 setter 대신 일정 내용 변경을 의미하는 메서드를 제공한다.

```java
schedule.update(
        title,
        scheduledAt,
        location,
        onlineLink,
        content,
        materials,
        now);
```

메서드는 수정 대상 필드와 `updatedAt`만 변경한다. 식별자, 소속 그룹, 등록자, 응답 마감과 생성
시각은 유지한다. JPA 변경 감지로 저장하며 별도의 명시적 `save` 호출이나 JPA Auditing을
추가하지 않는다.

### Repository

새 Repository 메서드는 추가하지 않는다. 기존 그룹 제한 단건 조회로 다른 그룹 일정 접근을
차단한다.

## 트랜잭션과 동시성

일정 조회, 검증과 변경은 하나의 Service 쓰기 트랜잭션에서 수행한다. 현재 스키마와 Entity에는
버전 컬럼이 없으므로 동시 수정은 마지막으로 커밋된 변경이 반영되는 기존 방식을 유지한다.
낙관적 잠금과 스키마 변경은 이번 범위에 포함하지 않는다.

## 테스트 전략

### DTO 테스트

- 문자열 양끝 공백 제거와 빈 선택 문자열의 `null` 정규화
- 제목 필수와 100자 제한
- 장소 255자, 온라인 정보 500자 제한
- 내용과 준비물 5,000자 제한
- 일정 시각 필수

### Entity 테스트

- 수정 대상 여섯 필드와 `updatedAt` 변경
- 등록자, 그룹, 응답 마감과 생성 시각 유지

### Service 테스트

- 활성 `LEADER`와 `MANAGER`의 수정 성공
- 활성 `MEMBER`, 비회원과 탈퇴 그룹원의 수정 거부
- 종료 그룹의 수정 거부
- 다른 그룹 소속 일정 접근 차단
- 이미 시작된 일정의 `SCHEDULE_UPDATE_NOT_ALLOWED`
- 새 일정 시간이 현재 이하일 때 `INVALID_SCHEDULE_TIME`
- 새 일정 시간이 기존 응답 마감보다 이를 때 `INVALID_SCHEDULE_TIME`
- 수정 성공 후 기존 응답 마감 유지
- 실패 단계 이후 Repository 호출이나 Entity 변경이 일어나지 않는 검증 순서

### Controller 테스트

- `PUT` 경로, 인증 사용자와 요청 본문 전달
- 성공 시 `200 OK`와 `ApiResponse<ScheduleResponse>`
- 인증 정보가 없을 때 공통 `UNAUTHORIZED`
- 잘못된 요청 본문에 대한 `400 Bad Request`

### 통합 테스트

- 실제 JPA 변경 감지로 수정 값 저장
- 등록자, 응답 마감과 생성 시각 유지
- 다른 그룹 일정 변경 방지
- 검증 실패 시 트랜잭션 롤백

## 문서와 변경 경계

구현 브랜치에서 `api.md`에 수정 요청, 응답, 권한과 오류 계약을 추가하고 `context.md`를 실제
Git 상태와 로드맵에 맞게 갱신한다. 공통 영역은 합의된 `SCHEDULE_UPDATE_NOT_ALLOWED` 추가를
위한 `global/exception/ErrorCode.java` 한 파일만 최소 변경한다.

이번 작업에는 다음 항목을 포함하지 않는다.

- 일정 삭제
- `responseDeadline` 수정
- Part4 참석 응답 처리 변경
- 활동 기록 처리 변경
- 스키마 변경과 낙관적 잠금
- 일정 부분 수정 `PATCH`

## 완료 조건

- 권한 있는 활성 `LEADER`와 `MANAGER`가 미래 일정을 전체 교체 방식으로 수정할 수 있다.
- 수정할 수 없는 그룹 상태, 역할과 일정 상태가 정의된 오류로 구분된다.
- 등록자, 응답 마감과 생성 시각이 보존된다.
- 요청 검증, Service, Controller, Entity와 실제 JPA 통합 테스트가 통과한다.
- `api.md`와 구현이 일치한다.
- 전체 테스트와 `spotlessCheck`가 통과한다.
