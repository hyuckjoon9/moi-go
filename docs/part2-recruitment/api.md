# Part2 모집글·지원 API 명세

> 기능별 권한·화면 처리 기준은 [feature-spec.md](feature-spec.md)를 참고한다.

## 공통

- 기본 경로: `/api`
- 인증: 로그인한 사용자가 호출한다. 인증되지 않으면 `401 UNAUTHORIZED`다.
- 날짜·시간: 모든 `LocalDateTime`/`LocalDate` 값은 ISO-8601 형식이다. 예: `2026-08-30T00:00:00`, `2026-08-30`
- 성공 응답: `ApiResponse<T>` 형식이다.

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

- 실패 응답: 오류 코드는 서버 내부·테스트 기준이며 응답 본문에는 노출하지 않는다.

```json
{
  "success": false,
  "data": null,
  "message": "모집글을 찾을 수 없습니다."
}
```

## 빠른 목록

| 기능 | 메서드 | 경로 | 성공 |
| --- | --- | --- | --- |
| 모집글 작성 | `POST` | `/recruitment-posts` | `200` |
| 모집글 목록 조회 | `GET` | `/recruitment-posts` | `200` |
| 모집글 상세 조회 | `GET` | `/recruitment-posts/{id}` | `200` |
| 모집글 수정 | `PATCH` | `/recruitment-posts/{id}` | `200` |
| 모집글 삭제 | `DELETE` | `/recruitment-posts/{id}` | `200` |
| 모집 마감 | `PATCH` | `/recruitment-posts/{id}/close` | `200` |
| 스터디 종료 | `PATCH` | `/recruitment-posts/{id}/end` | `200` |
| 지원 등록 | `POST` | `/recruitment-posts/{postId}/applications` | `200` |
| 지원자 목록 조회 | `GET` | `/recruitment-posts/{postId}/applications` | `200` |
| 지원 승인 | `PATCH` | `/recruitment-posts/{postId}/applications/{applicationId}/approve` | `200` |
| 지원 거절 | `PATCH` | `/recruitment-posts/{postId}/applications/{applicationId}/reject` | `200` |
| 내 신청 목록 조회 | `GET` | `/join-applications/me` | `200` |

> 삭제(`DELETE`)는 다른 파트와 달리 본문 없는 `204`가 아니라 `ApiResponse<Void>`(`data: null`)로 `200`을 반환한다. 팀 컨벤션을 `204`로 통일할지는 논의 필요(아래 "확인 필요" 참고).

## 모집글 작성

`POST /api/recruitment-posts`

### 요청

| 위치 | 이름 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- | --- |
| Body | `title` | string | 예 | 1~100자(권장, 검증 어노테이션 적용 여부 확인 필요) |
| Body | `category` | string | 예 | - |
| Body | `description` | string \| null | 아니요 | - |
| Body | `goal` | string \| null | 아니요 | - |
| Body | `method` | string \| null | 아니요 | - |
| Body | `meetingType` | string | 예 | 예: `ONLINE`, `OFFLINE` |
| Body | `location` | string \| null | 아니요 | 오프라인/하이브리드 시 사용 |
| Body | `onlineLink` | string \| null | 아니요 | - |
| Body | `meetingDay` | string \| null | 아니요 | - |
| Body | `capacity` | number | 예 | 1 이상(권장) |
| Body | `recruitmentDeadline` | string(date) | 예 | 현재보다 미래(권장) |
| Body | `expectedDuration` | string \| null | 아니요 | - |
| Body | `conditions` | string \| null | 아니요 | - |

```json
{
  "title": "스프링 스터디원 모집",
  "category": "개발",
  "description": "백엔드 스터디를 함께할 팀원을 구합니다.",
  "goal": "포트폴리오 프로젝트 완성",
  "method": "매주 발표 + 코드 리뷰",
  "meetingType": "ONLINE",
  "location": null,
  "onlineLink": "https://meet.example.com/moigo",
  "meetingDay": "매주 화요일",
  "capacity": 5,
  "recruitmentDeadline": "2026-08-30",
  "expectedDuration": "8주",
  "conditions": "Java 기초 지식 필요"
}
```

모집글이 저장되는 즉시 Part3의 `StudyGroupProvisioningPort.createGroup()`을 호출해 스터디 그룹을 함께 생성하고, 작성자(리더)를 그룹의 `LEADER`로 등록한다.

### 성공 응답 — 200

응답 `data`는 아래 "모집글 상세 조회"의 `data`와 같다.

## 모집글 목록 조회

`GET /api/recruitment-posts?category=개발&page=0&size=10`

### 요청

| 위치 | 이름 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| Query | `category` | string \| null | - | 없거나 공백이면 전체 조회 |
| Query | `page` | number | `0` | Spring `Pageable` 기본 |
| Query | `size` | number | `10` | Spring `Pageable` 기본 |
| Query | `sort` | string | `id,desc` | - |

### 성공 응답 — 200

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 4,
        "leaderId": 3,
        "title": "스프링 스터디원 모집",
        "category": "개발",
        "status": "RECRUITING"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 10
  },
  "message": null
}
```

Spring Data `Page` 직렬화 형태를 그대로 사용한다(요약 전용 응답 DTO로 축소할지는 확인 필요).

## 모집글 상세 조회

`GET /api/recruitment-posts/{id}`

### 성공 응답 — 200

```json
{
  "success": true,
  "data": {
    "id": 4,
    "leaderId": 3,
    "title": "스프링 스터디원 모집",
    "category": "개발",
    "description": "백엔드 스터디를 함께할 팀원을 구합니다.",
    "goal": "포트폴리오 프로젝트 완성",
    "method": "매주 발표 + 코드 리뷰",
    "meetingType": "ONLINE",
    "location": null,
    "onlineLink": "https://meet.example.com/moigo",
    "meetingDay": "매주 화요일",
    "capacity": 5,
    "recruitmentDeadline": "2026-08-30",
    "expectedDuration": "8주",
    "conditions": "Java 기초 지식 필요",
    "status": "RECRUITING",
    "createdAt": "2026-07-22T11:12:04",
    "updatedAt": "2026-07-22T11:12:04"
  },
  "message": null
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `status` | `RECRUITING` \| `CLOSED` \| `ACTIVE` \| `ENDED` | 모집글 상태(용어는 `feature-spec.md` 참고) |

## 모집글 수정

`PATCH /api/recruitment-posts/{id}`

### 요청

경로 변수는 "모집글 상세 조회"와 같다. 본문은 "모집글 작성"과 동일한 필드를 전체 교체 방식으로 보낸다.

### 권한

리더(작성자) 본인만 수정할 수 있다. 아니면 `403 RECRUITMENT_ACCESS_DENIED`.

### 성공 응답 — 200

응답 `data`는 "모집글 상세 조회"의 `data`와 같다.

## 모집글 삭제

`DELETE /api/recruitment-posts/{id}`

### 권한

리더 본인만 삭제할 수 있다. 아니면 `403 RECRUITMENT_ACCESS_DENIED`.

### 성공 응답 — 200

```json
{ "success": true, "data": null, "message": null }
```

### 실패 — 연결된 그룹/지원 내역이 있는 경우

Part3 `study_groups.post_id` FK가 `RESTRICT`이므로, 모집글에 이미 스터디 그룹이 생성돼 있으면(현재 설계상 모집글 작성 즉시 그룹이 생기므로 사실상 항상 해당) 삭제가 거부된다. `409 RECRUITMENT_DELETE_NOT_ALLOWED`로 응답한다(핸들러 적용 여부는 아래 "확인 필요" 참고).

## 모집 마감

`PATCH /api/recruitment-posts/{id}/close`

리더 본인만 호출할 수 있다. 모집글 상태를 `CLOSED`로 바꾼다. `CLOSED` 상태에서는 신규 지원(`POST .../applications`)이 `RECRUITMENT_CLOSED`로 거부된다.

### 성공 응답 — 200

응답 `data`는 "모집글 상세 조회"의 `data`와 같다.

## 스터디 종료

`PATCH /api/recruitment-posts/{id}/end`

리더 본인만 호출할 수 있다. 모집글 상태를 `ENDED`로 바꾸고, 동시에 Part3 `StudyGroupProvisioningPort.endGroup(postId)`를 호출해 연결된 스터디 그룹도 `ENDED`로 전환한다.

### 성공 응답 — 200

응답 `data`는 "모집글 상세 조회"의 `data`와 같다.

## 지원 등록

`POST /api/recruitment-posts/{postId}/applications`

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| Path | `postId` | number | 예 | 모집글 ID |
| Body | `motivation` | string | 예 | 지원 동기 |
| Body | `experience` | string \| null | 아니요 | 관련 경험 |
| Body | `availableTime` | string \| null | 아니요 | 참여 가능 시간대 |
| Body | `desiredRole` | string \| null | 아니요 | 희망 역할 |

```json
{
  "motivation": "그룹 자동 반영 테스트를 위해 지원합니다.",
  "experience": null,
  "availableTime": "평일 저녁",
  "desiredRole": "MEMBER"
}
```

### 제약

- 작성자 본인은 자신의 모집글에 지원할 수 없다(`SELF_APPLICATION_NOT_ALLOWED`).
- 모집글 상태가 `RECRUITING`이 아니면 거부한다(`RECRUITMENT_CLOSED`).
- 같은 사용자가 같은 모집글에 중복 지원할 수 없다(`DUPLICATE_APPLICATION`).

### 성공 응답 — 200

```json
{
  "success": true,
  "data": {
    "id": 6,
    "postId": 4,
    "applicantId": 5,
    "applicantNickname": "지원자4",
    "motivation": "그룹 자동 반영 테스트를 위해 지원합니다.",
    "experience": null,
    "availableTime": "평일 저녁",
    "desiredRole": "MEMBER",
    "status": "PENDING",
    "appliedAt": "2026-07-22T11:27:50.816"
  },
  "message": null
}
```

## 지원자 목록 조회

`GET /api/recruitment-posts/{postId}/applications`

리더 본인만 조회할 수 있다. 아니면 `403 APPLICATION_ACCESS_DENIED`.

### 성공 응답 — 200

```json
{
  "success": true,
  "data": [
    {
      "id": 5,
      "postId": 4,
      "applicantId": 4,
      "applicantNickname": "지원자3",
      "motivation": "그룹 자동 반영 테스트를 위해 지원합니다.",
      "experience": null,
      "availableTime": "평일 저녁",
      "desiredRole": "MEMBER",
      "status": "PENDING",
      "appliedAt": "2026-07-22T11:14:28"
    }
  ],
  "message": null
}
```

## 지원 승인

`PATCH /api/recruitment-posts/{postId}/applications/{applicationId}/approve`

리더 본인만 호출할 수 있다. `PENDING` 상태가 아니면 `409 APPLICATION_ALREADY_PROCESSED`.

승인 시 지원서 상태를 `APPROVED`로 바꾸고, 동시에 Part3 `StudyGroupProvisioningPort.addMember()`를 호출해 지원자를 스터디 그룹의 `MEMBER`로 자동 추가한다.

### 성공 응답 — 200

응답 `data`는 "지원자 목록 조회" 항목과 같은 형태이며 `status: "APPROVED"`다.

## 지원 거절

`PATCH /api/recruitment-posts/{postId}/applications/{applicationId}/reject`

리더 본인만 호출할 수 있다. `PENDING` 상태가 아니면 `409 APPLICATION_ALREADY_PROCESSED`. 그룹에는 아무 영향을 주지 않는다.

### 성공 응답 — 200

응답 `data`는 `status: "REJECTED"`인 지원서 정보다.

## 내 신청 목록 조회

`GET /api/join-applications/me?status=APPROVED`

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| Query | `status` | `PENDING` \| `APPROVED` \| `REJECTED` \| null | 아니요 | 없으면 전체 상태 조회 |

### 성공 응답 — 200

```json
{
  "success": true,
  "data": [
    {
      "id": 5,
      "postId": 4,
      "postTitle": "스프링 스터디원 모집",
      "status": "APPROVED",
      "appliedAt": "2026-07-22T11:14:28"
    }
  ],
  "message": null
}
```

## 오류 응답

| HTTP | 내부 코드 | 사용자 메시지(예시) | 주요 발생 조건 |
| --- | --- | --- | --- |
| 400 | `INVALID_REQUEST` | 잘못된 요청입니다. | 필수값 누락·형식 오류 (검증 어노테이션 실제 적용 여부 확인 필요) |
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. | 미인증 |
| 403 | `RECRUITMENT_ACCESS_DENIED` | 모집글에 대한 권한이 없습니다. | 리더가 아닌 사용자의 수정·삭제·마감·종료 시도 |
| 403 | `APPLICATION_ACCESS_DENIED` | 지원 내역에 대한 권한이 없습니다. | 리더가 아닌 사용자의 지원자 목록 조회·승인·거절 시도 |
| 404 | `RECRUITMENT_NOT_FOUND` | 모집글을 찾을 수 없습니다. | 존재하지 않는 모집글 |
| 404 | `MEMBER_NOT_FOUND` | 회원을 찾을 수 없습니다. | 존재하지 않는 사용자 |
| 404 | `APPLICATION_NOT_FOUND` | 지원 내역을 찾을 수 없습니다. | 존재하지 않거나 다른 모집글의 지원서 |
| 409 | `RECRUITMENT_CLOSED` | 모집이 마감된 글입니다. | `RECRUITING`이 아닌 모집글에 지원 시도 |
| 409 | `SELF_APPLICATION_NOT_ALLOWED` | 본인 모집글에는 지원할 수 없습니다. | 리더 본인의 지원 시도 |
| 409 | `DUPLICATE_APPLICATION` | 이미 지원한 모집글입니다. | 중복 지원 |
| 409 | `APPLICATION_ALREADY_PROCESSED` | 이미 처리된 지원입니다. | 승인/거절된 지원서 재처리 |
| 409 | `RECRUITMENT_DELETE_NOT_ALLOWED` | 연관된 그룹 또는 지원 내역이 있어 삭제할 수 없습니다. | 그룹 생성된 모집글 삭제 시도 (적용 여부 확인 필요) |

### 확인 필요 (문서화 시점 기준 미확정 항목)

- `RecruitmentCreateRequest`/`RecruitmentUpdateRequest`에 `@NotBlank`/`@NotNull` 등 검증 어노테이션이 실제로 적용됐는지.
- `GlobalExceptionHandler`의 `DataIntegrityViolationException` → `RECRUITMENT_DELETE_NOT_ALLOWED` 매핑이 실제로 반영됐는지.
- 목록 조회 응답을 Spring Data `Page` 그대로 노출할지, 별도 페이지 응답 DTO로 감쌀지(Part3의 `SchedulePageResponse`처럼).
- 지원 등록/모집글 삭제의 실제 성공 HTTP 상태 코드를 Part3 컨벤션(`201`/`204`)에 맞출지, 현재처럼 전부 `200`으로 유지할지.
