# 스터디 그룹 API

## 엔드포인트 목록

그룹 생성은 외부 HTTP 엔드포인트가 아니라 Part2가 호출하는 내부 서비스 계약으로 먼저
구현한다. 그룹 홈은 인증된 활성 그룹원이 그룹 정보와 활성 그룹원 목록을 함께 조회하는
엔드포인트로 제공한다. 일정 엔드포인트는 해당 기능 구현 시 추가한다.

| 기능 | 메서드 | 경로 | 인증 |
| --- | --- | --- | --- |
| 그룹 홈 조회 | `GET` | `/api/groups/{groupId}` | 필수 |

## 내부 서비스 계약

### 모집 결과 기반 그룹 생성

- 호출자: Part2 모집·신청 유스케이스
- 진입점: `StudyGroupCreationService.create(CreateStudyGroupCommand)`
- 반환값: 생성되었거나 이미 존재하는 그룹의 `Long` ID

| 입력 | 타입 | 규칙 |
| --- | --- | --- |
| `postId` | `Long` | 필수, 그룹 생성의 멱등 키 |
| `groupName` | `String` | 필수, 양끝 공백 제거 후 빈 값 불가 |
| `leaderUserId` | `Long` | 필수, 최초 `LEADER` |
| `approvedUserIds` | `List<Long>` | 필수 목록, 빈 목록 허용, null 원소 불가 |

- 같은 `postId` 재요청은 최초 그룹과 그룹원을 유지하고 기존 그룹 ID를 반환한다.
- 모집장은 `LEADER`, 중복과 모집장을 제거한 승인 회원은 `MEMBER`로 등록한다.
- 그룹과 초기 그룹원은 하나의 트랜잭션에서 생성한다.
- Part2가 모집글과 사용자 식별자의 유효성을 보장한다.
- Part3는 Part1·Part2 Repository를 직접 조회하지 않는다.

## 그룹 홈 조회

### 요청

```http
GET /api/groups/{groupId}
Authorization: Bearer {accessToken}
```

| 입력 | 위치 | 타입 | 규칙 |
| --- | --- | --- | --- |
| `groupId` | Path | `Long` | 필수, 조회할 그룹 식별자 |
| 인증 사용자 | Principal | `AuthenticatedMember` | 필수, Part3는 `id()`만 사용 |

- Controller는 `@AuthenticationPrincipal AuthenticatedMember`로 인증 사용자를 전달받는다.
- 인증 정보가 없거나 토큰이 유효하지 않은 경우에는 Part1의 공통 인증 오류 계약을 따른다.
- Part3는 인증 내부 구현이나 Part1의 회원 Repository를 직접 사용하지 않는다.

### 성공 응답

- HTTP 상태: `200 OK`
- 응답 타입: `ApiResponse<StudyGroupHomeResponse>`

```json
{
  "success": true,
  "data": {
    "groupId": 10,
    "postId": 25,
    "name": "토익 스터디",
    "status": "ACTIVE",
    "createdAt": "2026-07-01T10:00:00",
    "myRole": "MEMBER",
    "members": [
      {
        "userId": 1,
        "role": "LEADER",
        "joinedAt": "2026-07-01T10:00:00"
      },
      {
        "userId": 2,
        "role": "MEMBER",
        "joinedAt": "2026-07-02T14:30:00"
      }
    ]
  },
  "message": null
}
```

#### `StudyGroupHomeResponse`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `groupId` | `Long` | 그룹 식별자 |
| `postId` | `Long` | 그룹이 생성된 모집글 식별자 |
| `name` | `String` | 그룹 이름 |
| `status` | `GroupStatus` | 그룹 상태, `ACTIVE` 또는 `ENDED` |
| `createdAt` | `LocalDateTime` | 그룹 생성 시각 |
| `myRole` | `GroupRole` | 현재 인증 사용자의 그룹 내 역할 |
| `members` | `List<GroupMemberSummaryResponse>` | 활성 그룹원 목록 |

#### `GroupMemberSummaryResponse`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | `Long` | 그룹원의 사용자 식별자 |
| `role` | `GroupRole` | 그룹 내 역할 |
| `joinedAt` | `LocalDateTime` | 그룹 가입 시각 |

- Part1이 소유하는 닉네임과 프로필 정보는 이번 응답에 포함하지 않는다.
- `members`에는 `GroupMemberStatus.ACTIVE`인 그룹원만 포함하고 탈퇴 그룹원은 제외한다.
- 그룹원은 `LEADER`, `MANAGER`, `MEMBER` 순으로 정렬한다.
- 같은 역할은 `joinedAt` 오름차순, 가입 시각도 같으면 `userId` 오름차순으로 정렬한다.

### 접근 규칙

- 현재 인증 사용자에게 해당 그룹의 그룹원 기록이 있어야 한다.
- 현재 인증 사용자의 그룹원 상태가 `ACTIVE`여야 한다.
- 그룹 상태가 `ENDED`여도 활성 그룹원은 그룹 홈을 조회할 수 있다.
- 그룹 상태가 `ENDED`인 경우에도 응답의 `status`에 `ENDED`를 그대로 반환한다.
- 종료 그룹의 생성·수정 등 쓰기 작업 허용 여부는 해당 API 계약에서 별도로 정의한다.

### 오류 응답

오류 본문은 공통 `ApiResponse.error(message)` 형식을 따른다.

| 상황 | HTTP 상태 | 오류 코드 | 메시지 |
| --- | --- | --- | --- |
| 그룹이 존재하지 않음 | `404 Not Found` | `GROUP_NOT_FOUND` | `그룹을 찾을 수 없습니다.` |
| 인증 사용자의 그룹원 기록이 없음 | `403 Forbidden` | `GROUP_ACCESS_DENIED` | `그룹에 접근할 권한이 없습니다.` |
| 인증 사용자가 탈퇴 그룹원임 | `403 Forbidden` | `WITHDRAWN_GROUP_MEMBER` | `탈퇴한 그룹원은 그룹에 접근할 수 없습니다.` |
| 인증 실패 | `401 Unauthorized` | Part1 공통 인증 오류 | Part1 공통 인증 계약을 따름 |

`GROUP_NOT_FOUND`, `GROUP_ACCESS_DENIED`, `WITHDRAWN_GROUP_MEMBER`는 그룹 홈 구현 시
공통 영역 담당자와 합의된 변경으로 `global/exception/ErrorCode.java`에 추가한다. 인증 실패를
위한 Part3 전용 오류 코드는 추가하지 않으며 `SecurityConfig`와 JWT 필터도 이번 기능 범위에서
수정하지 않는다.
