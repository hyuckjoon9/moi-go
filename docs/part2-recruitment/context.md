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

## 완료된 항목 (갱신됨)

- Postman 통합 테스트 11단계 플랜 전 항목 완료. 삭제(409 `RECRUITMENT_DELETE_NOT_ALLOWED`) 포함.
- `RecruitmentCreateRequest`/`RecruitmentUpdateRequest` 검증 어노테이션 적용 확인 완료.
- `GlobalExceptionHandler`의 `DataIntegrityViolationException` 매핑 적용 확인 완료.
- `JoinApplicationServiceTest.create_success` 정상 버전으로 복구 확인 완료.
- (Part3 확인 요청 → 해결) `GET /api/groups/me`가 `ENDED` 그룹을 제외하던 필터가 제거됨 — 이제 활성 그룹원이면 그룹 상태와 무관하게 전부 반환되고, 응답에 `status` 필드가 있어 프론트가 ACTIVE/ENDED를 구분할 수 있음.
- (Part3 확인 요청 → 해결) `GROUP_ENDED` 메시지 문제 → `addMember()` 실패 시 별도 코드 `GROUP_MEMBER_ADD_NOT_ALLOWED`("종료된 그룹에는 새 그룹원을 추가할 수 없습니다.")로 분리됨. `GROUP_ENDED`는 이제 일정 관리 실패 상황에서만 쓰임.
- (Part4 진행 상황) 참석 응답 마감·그룹원 권한 검증이 `ScheduleAttendancePolicyReader` 연동으로 완료됨(이전엔 미검증 상태였음). Part2와 직접 관련은 없지만 전체 앱 완성도에 참고.

## 바로 다음 작업

1. 그룹이 연결된 모집글의 삭제 정책(항상 막을지, 그룹도 함께 정리하는 경로를 둘지)을 팀과 확정하고 `feature-spec.md`, `erd.md`에 반영한다.
2. `RecruitmentPost.close()`/`end()`에 상태 전이 가드가 필요한지(예: 이미 `ENDED`인 글에 `close()` 재호출 허용 여부) 기획 확인 후 반영한다.
3. `title` 필드 `@Size` 길이 제한 추가 여부 결정.
4. 목록 조회 응답을 `Page` 그대로 노출할지 별도 DTO로 감쌀지, 삭제/지원 등록 성공 상태 코드를 Part3 컨벤션(`201`/`204`)에 맞출지 팀 컨벤션 논의.
5. 프론트(`app.js`)에 Part2 API(지원 승인/거절 등) 연결 — 프론트 진행 상황에 따름.

## 남은 팀 확인 사항

- (에러 응답에 `code` 필드를 추가할지) 프론트에서 에러별 분기 처리가 필요한지 확인 후 결정.
- `local` 테스트 실행 시 `SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD` 환경변수가 필수로 바뀐 것 — 팀 컨벤션 문서화(README/AGENTS.md) 필요.

## 다음 세션용 시작 요청 예시

```text
AGENTS.md와 docs/part2-recruitment/context.md를 읽고 현재 Git 상태를 확인해줘.
현재 브랜치의 '바로 다음 작업'부터 이어서 진행해줘.
```

## 세션 종료 시 갱신

- 기준 날짜와 `develop` HEAD
- 구현 완료 상태에서 달라진 항목
- 새 결정 또는 blocker
- 바로 다음 작업
- 보호해야 할 미커밋 파일

과거 브랜치·커밋·PR의 상세 일지는 Git 이력으로 확인하고 이 문서에 누적하지 않는다.
