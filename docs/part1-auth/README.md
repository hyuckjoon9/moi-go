# Part 1 — 회원·인증·공통

회원 계정과 프로필, JWT 기반 인증 및 refresh token 수명 주기를 담당한다. 다른 파트에는 인증된 회원 식별자와 공통 보안·응답 기반을 제공한다.

## 담당 기능

- 회원가입, 내 정보 조회·수정 및 프로필 이미지 업로드
- 로그인, access/refresh token 발급·재발급, 로그아웃
- 인증 principal, JWT 검증, 공통 응답·예외 처리 기반

## 주요 흐름

회원가입 → 로그인 → access/refresh token 발급 → Bearer 토큰 인증 → `AuthenticatedMember.id()`를 각 파트에 전달한다. refresh token 재발급 시 기존 token은 폐기하고 새 token을 저장한다.

## 제공 API와 내부 계약

- 인증 API는 `/api/auth/signup`, `/login`, `/reissue`, `/logout`을 제공하고, 회원 API는 `/api/members/me` 조회·수정과 `/me/profile-image` 업로드를 제공한다.
- 문서화된 인증·회원 응답은 공통 `ApiResponse` 형식을 사용한다.
- 다른 파트는 `@AuthenticationPrincipal AuthenticatedMember`로 현재 회원 ID를 받으며, Part1의 Entity나 Repository에 직접 결합하지 않는다.

## 핵심 설계 및 처리 기준

- 이메일과 닉네임은 고유하며, 비밀번호는 `PasswordEncoder`로 암호화한다.
- `ACTIVE` 회원만 로그인할 수 있고, 탈퇴 회원은 인증과 내 정보 접근이 제한된다.
- DB에 저장된 refresh token만 재발급에 사용하며, 현재 구현은 사용자당 활성 refresh token 하나를 유지한다.
- 회원은 Part2의 모집·지원, Part3의 그룹·일정, Part4의 출석·활동 데이터와 FK로 연결되므로 탈퇴는 물리 삭제보다 `WITHDRAWN` 상태 전환을 우선한다.

## 문서 목록

| 문서 | 설명 |
| --- | --- |
| [API 명세](api.md) | 회원가입·인증·내 정보 API의 요청, 응답, 인증 기준 |
| [ERD](erd.md) | `users`, `refresh_tokens`와 외부 FK 경계 |
| [개발 가이드](development-guide.md) | 소유 범위, 구현·테스트·협업 기준 |
| [작업 컨텍스트](context.md) | 구현 현황, 결정 사항, 다음 작업 |

## 관련 파트

- Part2·Part3·Part4는 인증된 회원 ID를 받아 각 도메인의 작성자·신청자·그룹원·출석 및 활동 주체를 식별한다.
- 그룹별 역할과 권한은 Part3, 출석·활동 처리 규칙은 Part4가 소유하며 Part1의 시스템 역할과 분리된다.
