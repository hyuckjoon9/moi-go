# Back Office API

이 문서는 현재 서버에 구현된 관리자 HTTP 계약만 다룬다. 후속 기능은
[context.md](context.md)의 작업 목록에서 관리한다.

## 공통

- 기본 경로: `/api/admin`
- 인증: JWT access token을 가진 활성 `ADMIN`
- 응답: 기존 `ApiResponse<T>` 형식
- 날짜·시간: ISO-8601 `LocalDateTime`
- 목록 페이지는 0부터 시작하며 기본 크기는 20, 최대 크기는 100이다.

| 상태 | 의미 |
| --- | --- |
| `401` | 인증 정보가 없거나 유효하지 않음 |
| `403` | 인증됐지만 관리자 권한이 없거나 변경 대상이 허용되지 않음 |
| `404` | 대상 회원이 없음 |
| `409` | 화면에서 확인한 상태와 서버 상태가 충돌함 |

## 운영 현황

### `GET /api/admin/dashboard`

회원·모집글·그룹의 상태별 건수와 최근 운영 조치 최대 10건을 반환한다. 최근 조치는 `action`,
`targetType`, `targetId`, `targetLabel`, `adminId`, `reason`, `createdAt`을 포함한다.

## 회원

### `GET /api/admin/members`

| Query | 설명 |
| --- | --- |
| `keyword` | 이메일 또는 닉네임 부분 일치 |
| `role` | `USER` 또는 `ADMIN` |
| `status` | `ACTIVE`, `SUSPENDED`, `WITHDRAWN` |
| `page`, `size` | 페이지 번호와 크기 |

응답 항목은 `memberId`, `email`, `nickname`, `role`, `status`, `createdAt`을 포함한다.
관리자는 항상 먼저, 각 역할 안에서는 회원 ID 오름차순으로 정렬한다.

### `GET /api/admin/members/{memberId}`

기본 프로필, 역할·상태, 가입·수정 시각, 참여 그룹 요약과 최근 운영 조치를 반환한다. 비밀번호와
토큰은 반환하지 않는다. 최근 조치 항목은 `action`, `reason`, `createdAt`을 포함한다.

### `PATCH /api/admin/members/{memberId}/status`

```json
{
  "expectedStatus": "ACTIVE",
  "status": "SUSPENDED",
  "reason": "운영 정책 위반에 따른 이용 제한"
}
```

- `expectedStatus`와 `status`는 `ACTIVE` 또는 `SUSPENDED`다.
- `reason`은 앞뒤 공백을 제거한 5~500자다.
- `USER`만 변경할 수 있다. `ADMIN`, `WITHDRAWN`, 자기 자신의 상태 변경은 거부한다.
- 이미 요청한 상태면 현재 상세를 반환하고 이력을 추가하지 않는다.
- 실제 상태 변경은 refresh token 폐기와 운영 이력 저장을 같은 트랜잭션에서 처리한다.

## 모집글

### `GET /api/admin/recruitments`

| Query | 설명 |
| --- | --- |
| `keyword` | 모집글 제목 또는 모집장 닉네임 부분 일치 |
| `status` | `RECRUITING`, `CLOSED`, `ACTIVE`, `ENDED` |
| `visibility` | `VISIBLE`, `HIDDEN` |
| `page`, `size` | 페이지 번호와 크기 |

응답 항목은 `recruitmentId`, `leaderId`, `leaderNickname`, `title`, `category`, `status`,
`visibility`, `createdAt`을 포함한다. 기본 정렬은 모집글 ID 내림차순이다.

### `GET /api/admin/recruitments/{recruitmentId}`

모집글 본문·모집 조건·모집장 ID·모집 상태·노출 상태·연결 그룹 ID와 생성·수정 시각, 최근 운영 조치
최대 10건을 반환한다. 최근 조치는 `action`, `reason`, `createdAt`을 포함한다.

### `PATCH /api/admin/recruitments/{recruitmentId}/visibility`

```json
{
  "expectedVisibility": "VISIBLE",
  "visibility": "HIDDEN",
  "reason": "운영 정책 위반으로 숨김 처리"
}
```

- `expectedVisibility`, `visibility`는 `VISIBLE` 또는 `HIDDEN`이다.
- `reason`은 앞뒤 공백을 제거한 5~500자다.
- 이미 요청한 노출 상태면 현재 상세를 반환하고 이력을 추가하지 않는다.
- 실제 변경은 모집글 소유 도메인의 관리 포트 호출과 운영 이력 저장을 같은 트랜잭션에서 처리한다.

## 읽기 전용 운영 조회

모든 목록은 `page`, `size`(기본 20, 최대 100)를 받고, 0-기반 페이지 메타데이터를 반환한다.

| 경로 | 조회 범위 | 필터 |
| --- | --- | --- |
| `GET /api/admin/groups` | 그룹, 연결 모집글 ID, 활성 인원 수 | `keyword`(그룹명), `status`(`ACTIVE`, `ENDED`) |
| `GET /api/admin/schedules` | 일정, 그룹명, 등록자, 응답 마감 | `keyword`(그룹명·일정 제목) |
| `GET /api/admin/attendance-records` | 출석 기록, 일정·그룹·회원, 체크 관리자 | `keyword`(그룹·일정·회원), `status` |
| `GET /api/admin/activity-records` | 활동 기록, 일정·그룹, 작성자, 갱신 시각 | `keyword`(그룹·일정·활동 주제) |
| `GET /api/admin/audit-logs` | 불변 운영 이력 | `action`, `targetType`, `keyword`(대상·사유) |

이 API들은 조회 전용이다. 그룹·일정·출석·활동 또는 이력 데이터를 변경·삭제하는 관리자 API는 제공하지 않는다.

출석 기록의 `checkedBy`는 사람이 처리한 경우 관리자 ID이며, 자동 결석 처리 기록은 `null`이다.
