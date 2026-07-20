# 인증 API

## 엔드포인트 목록

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/signup` | 불필요 | 회원가입 |
| `POST` | `/api/auth/login` | 불필요 | 로그인과 access/refresh token 발급 |
| `POST` | `/api/auth/reissue` | 불필요 | refresh token 기반 token 재발급 |
| `POST` | `/api/auth/logout` | 필요 | refresh token 삭제 |
| `GET` | `/api/members/me` | 필요 | 내 회원 정보 조회 |
| `PATCH` | `/api/members/me` | 필요 | 내 프로필 수정 |

## 공통 응답 형식

모든 응답은 공통 형식 `ApiResponse`로 감싼다.

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

오류 응답은 `success=false`, `message`에 원인을 담는다.

```json
{
  "success": false,
  "data": null,
  "message": "이미 사용 중인 이메일입니다."
}
```

## 인증 방식

로그인 성공 시 `accessToken`과 `refreshToken`을 발급한다. 인증이 필요한 API는 access token을 `Authorization` 헤더에 Bearer 형식으로 전달한다.

```http
Authorization: Bearer {accessToken}
```

다른 파트 컨트롤러에서는 현재 로그인 회원을 다음처럼 받을 수 있다.

```java
@AuthenticationPrincipal AuthenticatedMember authenticatedMember
```

`authenticatedMember.id()`가 ERD의 `users.id`이며, Part2/3/4의 `leader_id`, `applicant_id`, `creator_id`, `user_id`, `checked_by`, `author_id`에 연결한다.

## POST /api/auth/signup

회원가입.

### Request

```json
{
  "email": "user@moigo.test",
  "password": "password123",
  "nickname": "모이고",
  "bio": "백엔드 스터디를 찾고 있습니다.",
  "interests": "Java,Spring,MySQL",
  "profileImageUrl": null
}
```

### Request 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | `string` | 예 | 로그인 이메일, 이메일 형식, 최대 255자 |
| `password` | `string` | 예 | 비밀번호, 8~100자 |
| `nickname` | `string` | 예 | 닉네임, 최대 50자 |
| `bio` | `string` | 아니오 | 자기소개 |
| `interests` | `string` | 아니오 | 관심 분야, 최대 255자 |
| `profileImageUrl` | `string` | 아니오 | 프로필 이미지 URL, 최대 500자 |

### Response 201

```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@moigo.test",
    "nickname": "모이고",
    "bio": "백엔드 스터디를 찾고 있습니다.",
    "interests": "Java,Spring,MySQL",
    "profileImageUrl": null,
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-07-20T09:00:00",
    "updatedAt": "2026-07-20T09:00:00"
  },
  "message": null
}
```

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `400` | 요청 본문 validation 실패 |
| `409` | 이메일 중복 |
| `409` | 닉네임 중복 |

## POST /api/auth/login

이메일과 비밀번호로 로그인한다.

### Request

```json
{
  "email": "user@moigo.test",
  "password": "password123"
}
```

### Response 200

```json
{
  "success": true,
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "tokenType": "Bearer"
  },
  "message": null
}
```

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `400` | 요청 본문 validation 실패 |
| `401` | 이메일 또는 비밀번호 불일치 |
| `401` | 탈퇴 또는 비활성 회원 로그인 시도 |

## POST /api/auth/reissue

refresh token으로 access token과 refresh token을 재발급한다. 기존 refresh token은 삭제하고 새 refresh token을 저장한다.

### Request

```json
{
  "refreshToken": "..."
}
```

### Response 200

```json
{
  "success": true,
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "tokenType": "Bearer"
  },
  "message": null
}
```

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `400` | 요청 본문 validation 실패 |
| `401` | refresh token 형식 오류 또는 서명 검증 실패 |
| `401` | refresh token 만료 |
| `401` | DB에 저장되지 않은 refresh token |
| `404` | token의 회원을 찾을 수 없음 |

## POST /api/auth/logout

refresh token을 삭제한다. access token 인증이 필요하다.

### Request

```json
{
  "refreshToken": "..."
}
```

### Response 200

```json
{
  "success": true,
  "data": null,
  "message": null
}
```

## GET /api/members/me

내 회원 정보를 조회한다. 인증 필요.

### Response 200

```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@moigo.test",
    "nickname": "모이고",
    "bio": "백엔드 스터디를 찾고 있습니다.",
    "interests": "Java,Spring,MySQL",
    "profileImageUrl": null,
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-07-20T09:00:00",
    "updatedAt": "2026-07-20T09:00:00"
  },
  "message": null
}
```

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `401` | Authorization 헤더 없음 또는 access token 검증 실패 |
| `404` | 회원을 찾을 수 없음 |
| `403` | 탈퇴 회원 |

## PATCH /api/members/me

내 프로필 정보를 수정한다. 인증 필요. 요청에 포함한 필드만 수정한다.

### Request

```json
{
  "nickname": "새닉네임",
  "bio": "수정된 자기소개",
  "interests": "Spring Security,JPA",
  "profileImageUrl": "https://example.com/profile.png"
}
```

### Request 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `nickname` | `string` | 아니오 | 닉네임, 최대 50자, 포함 시 공백 불가 |
| `bio` | `string` | 아니오 | 자기소개 |
| `interests` | `string` | 아니오 | 관심 분야, 최대 255자 |
| `profileImageUrl` | `string` | 아니오 | 프로필 이미지 URL, 최대 500자 |

### Response 200

`GET /api/members/me`와 같은 회원 응답을 반환한다.

### 주요 오류

| 상태 | 조건 |
| --- | --- |
| `400` | 요청 본문 validation 실패 |
| `401` | Authorization 헤더 없음 또는 access token 검증 실패 |
| `409` | 닉네임 중복 |
| `404` | 회원을 찾을 수 없음 |
| `403` | 탈퇴 회원 |
