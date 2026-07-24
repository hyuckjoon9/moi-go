# Part 2 — 모집글·참가 신청

스터디 모집글의 작성부터 지원자 처리와 스터디 종료까지를 담당한다. 모집글·지원 상태 변경은 Part3의 그룹 프로비저닝 계약과 연결된다.

## 담당 기능

- 모집글 작성, 목록·상세 조회, 수정, 삭제, 마감·종료·재개방
- 지원 등록, 지원자 목록 조회, 승인·거절, 내 신청 목록 조회
- 모집글 생성·지원 승인·스터디 종료에 따른 그룹 생성·그룹원 추가·그룹 종료 요청

## 주요 흐름

모집글 작성 → Part3 그룹 및 리더 생성 → 지원 등록 → 리더의 승인 또는 거절 → 승인 시 그룹원 추가 → 스터디 종료 시 모집글·그룹 종료로 이어진다.

## 제공 API와 내부 계약

- 모집글은 `/api/recruitment-posts`, 지원은 `/api/recruitment-posts/{postId}/applications` 및 `/api/join-applications/me` 경로로 제공한다.
- Part3에는 `StudyGroupProvisioningPort`로만 연결한다. `createGroup`, `addMember`, `endGroup`은 각각 모집글 작성, 지원 승인, 스터디 종료 트랜잭션에 참여한다.
- `recruitment`·`application`은 Part3 Entity나 Repository를 직접 참조하지 않는다.

## 핵심 설계 및 처리 기준

- 모든 API는 로그인 사용자를 기준으로 하며, 리더만 모집글 관리와 지원자 처리 권한을 가진다.
- 지원은 `RECRUITING` 상태의 타인 모집글에만 가능하고, 동일 모집글에 중복 지원할 수 없다.
- 지원은 `PENDING`에서 `APPROVED` 또는 `REJECTED`로 한 번만 처리한다. 승인 시 Part3 호출이 실패하면 지원 상태 변경도 함께 롤백되어야 한다.
- 모집글당 그룹 하나는 Part3의 `study_groups.post_id` 고유 제약으로 보장한다. 생성 즉시 그룹이 연결되므로 모집글 삭제는 FK 제약으로 제한된다.

## 문서 목록

| 문서 | 설명 |
| --- | --- |
| [API 명세](api.md) | 모집글·지원 API, 권한, 오류와 응답 기준 |
| [기능 명세](featurespec.md) | 사용자 흐름, 상태, 화면·권한 처리 기준 |
| [ERD](erd.md) | `recruitment_posts`, `join_applications` 데이터 계약 |
| [개발 가이드](developmentguide.md) | 소유 범위, Part3 경계, 테스트·협업 기준 |
| [작업 컨텍스트](context.md) | 구현 현황과 남은 결정 사항 |
| [그룹 연동 설계](studygroupintegrationdesign.md) | Part2–Part3 프로비저닝 포트와 트랜잭션 설계 |

## 관련 파트

- Part1의 인증 사용자 ID를 모집글 리더와 지원자로 사용한다.
- Part3의 `StudyGroupProvisioningPort`를 통해 그룹 생성·그룹원 추가·그룹 종료를 요청한다.
- Part4의 출석·활동 영역과는 현재 직접 연동하지 않는다.
