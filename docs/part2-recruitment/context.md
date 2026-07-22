# Part2 작업 컨텍스트

> 기준: 2026-07-22
>
> 실제 Git 상태가 이 문서와 다르면 저장소를 우선한다.

## 세션 시작

1. 루트 `AGENTS.md`와 [`development-guide.md`](development-guide.md)를 읽는다.
2. 현재 브랜치·변경·최근 커밋을 확인한다.
3. 작업과 관련된 [`api.md`](api.md), [`erd.md`](erd.md), [`study-group-integration-design.md`](study-group-integration-design.md)를 읽는다.
4. 아래 "바로 다음 작업"부터 진행한다.

```powershell
git branch --show-current
git status --short
git log -3 --oneline
```

## 범위와 현재 상태

- Part2 소유 패키지: `recruitment`, `application`
- Part2 소유 테이블: `recruitment_posts`, `join_applications`
- 외부 경계: Part1 `auth/member`, Part3 `study`(`StudyGroupProvisioningPort`를 통해서만 연동), `global`
- 모집글 작성·조회·수정·삭제·마감·종료, 지원 등록·목록조회·승인·거절·내 신청 목록 조회가 구현됐다.
- 그룹 생성 구조를 "승인 완료 후 일괄 확정(`confirmGroup()`)" 방식에서 "모집글 작성 시 그룹 즉시 생성 + 승인 건별 그룹원 추가" 방식으로 전환했다. 자세한 내용은 [`study-group-integration-design.md`](study-group-integration-design.md) 참고.

## 구현 완료 계약

### 모집글

- `POST /api/recruitment-posts`로 모집글을 생성하면 같은 트랜잭션에서 `StudyGroupProvisioningPort.createGroup()`을 호출해 스터디 그룹을 함께 생성하고 작성자를 `LEADER`로 등록한다.
- 리더 본인만 수정·삭제·마감·종료할 수 있다. 아니면 `RECRUITMENT_ACCESS_DENIED`.
- `PATCH .../end`는 모집글 상태를 `ENDED`로 바꾸는 동시에 `StudyGroupProvisioningPort.endGroup(postId)`를 호출한다.

### 지원

- `RECRUITING` 상태의 모집글에만, 본인 모집글이 아닌 경우에만, 중복이 아닌 경우에만 지원할 수 있다.
- `PATCH .../approve`는 지원 상태를 `APPROVED`로 바꾸는 동시에 `StudyGroupProvisioningPort.addMember()`를 호출해 지원자를 그룹 멤버로 추가한다.
- `PATCH .../reject`는 지원 상태만 `REJECTED`로 바꾸고 그룹에는 영향을 주지 않는다.

상세 필드·오류는 `api.md`, 데이터 제약은 `erd.md`, 설계 이유는 `study-group-integration-design.md`를 기준으로 한다.

## 바로 다음 작업

1. Postman 통합 테스트 11단계 플랜의 나머지 항목 완료·기록: (10) 그룹이 연결된 모집글 삭제 시 `409 RECRUITMENT_DELETE_NOT_ALLOWED` 응답 확인, (11) `GET /api/join-applications/me` 상태 필터 조회 확인.
2. `RecruitmentCreateRequest`/`RecruitmentUpdateRequest`에 `@NotBlank`/`@NotNull` 등 검증 어노테이션을 실제로 적용했는지 재확인하고, 미적용이면 반영한다.
3. `GlobalExceptionHandler`에 `DataIntegrityViolationException` → `409 RECRUITMENT_DELETE_NOT_ALLOWED` 매핑이 실제로 반영됐는지 재확인한다.
4. `JoinApplicationServiceTest`의 `create_success` 테스트가 올바른 버전(지원 등록 케이스)으로 복구됐는지 확인한다.
5. 그룹이 연결된 모집글의 삭제 정책(항상 막을지, 그룹도 함께 정리하는 경로를 둘지)을 팀과 확정하고 `feature-spec.md`, `erd.md`에 반영한다.

## 팀원(Part3) 확인 요청 사항

- `GET /api/groups/me`가 `ENDED` 상태 그룹을 목록에서 제외하는 것으로 보인다(DB에는 `status = ENDED`로 정상 반영됨을 확인함). 의도된 동작인지 확인 필요.
- `GROUP_ENDED` 오류 메시지가 "종료된 그룹에서는 일정을 관리할 수 없습니다."로, 그룹원 추가 실패 상황에서도 일정 관리 문구가 그대로 노출된다. 문구 조정 필요 여부 확인.

## 세션 종료 시 갱신

- 기준 날짜와 `develop` HEAD
- 구현 완료 상태에서 달라진 항목
- 새 결정 또는 blocker
- 바로 다음 작업
- 보호해야 할 미커밋 파일

과거 브랜치·커밋·PR의 상세 일지는 Git 이력으로 확인하고 이 문서에 누적하지 않는다.
