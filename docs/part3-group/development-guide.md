# Part3 개발 가이드

## 목적과 기준 문서

Part3 그룹·일정 작업의 범위와 협업 경계를 정의한다. 공통 규칙은 중복하지 않고 다음 문서를 따른다.

- 브랜치·커밋·PR 흐름: [`README.md`](../../README.md)
- 계층과 패키지 책임: [`docs/architecture/folder-structure.md`](../architecture/folder-structure.md)
- HTTP·내부 서비스 계약: [`api.md`](api.md)
- 테이블·FK·인덱스: [`erd.md`](erd.md)
- 현재 상태와 다음 작업: [`context.md`](context.md)

현재 기술 기준은 Java 21, Spring Boot 4.1.0, Gradle 9.5.1, MySQL 8.0+이다. 사용자 요청이나 최신 팀 합의가 이 문서와 충돌하면 합의를 우선하고 관련 문서를 함께 갱신한다.

## 소유 범위

| 영역 | 경로 | 책임 |
| --- | --- | --- |
| 그룹 | `src/main/java/com/mycom/myapp/study/**` | 그룹·그룹원·그룹 홈 |
| 일정 | `src/main/java/com/mycom/myapp/schedule/**` | 일정 생성·조회·변경·삭제와 응답 마감 정책 |
| 테스트 | `src/test/java/com/mycom/myapp/{study,schedule}/**` | 위 영역의 단위·통합 테스트 |
| 문서 | `docs/part3-group/**` | Part3 계약·설계·작업 상태 |

루트 `AGENTS.md` 갱신도 허용한다. 다른 파트나 `global`, 빌드 설정, 공통 테스트 지원 코드는 공유 영역이다.

### 파트 간 경계

| 영역 | 원칙 |
| --- | --- |
| Part1 `auth`, `member` | 인증 사용자 ID와 공개 인터페이스만 사용한다. |
| Part2 `recruitment`, `application` | 모집 결과 등 합의된 공개 계약으로 그룹 생성과 연결한다. |
| Part4 `attendance` | 일정 식별자와 응답 정책을 제공하고 출석 판정은 Part4에 맡긴다. |
| `activity` | 일정 식별자를 제공하되 활동 기록 규칙은 담당 영역에 맡긴다. |
| `global` | 공통 응답·예외·보안 변경은 담당자 합의와 리뷰를 받는다. |

다른 영역의 Entity나 Repository에 직접 결합하지 않는다. 외부 변경이 필요하면 공개 경계로 해결 가능한지 먼저 확인하고, 담당자와 계약을 합의한 뒤 최소 범위와 테스트만 수정한다. 독립 검토가 가능하면 별도 브랜치·PR로 분리하고, 같은 PR이면 경로·이유·영향·검증 결과를 명시한다.

## 핵심 불변식

- 모집글 하나당 그룹은 하나이며 `study_groups.post_id`가 이를 보장한다.
- 같은 그룹에 사용자를 중복 등록하지 않고 탈퇴는 `WITHDRAWN` 상태로 보존한다.
- 그룹 역할은 `LEADER`, `MANAGER`, `MEMBER`다.
- 일정 관리는 활성 그룹의 활성 `LEADER`·`MANAGER`만 수행한다.
- `responseDeadline`은 없거나 `scheduledAt` 이하이며, 없으면 일정 시각이 실질 마감이다.
- 참석 응답 등록·변경·삭제는 현재 시각이 실질 마감보다 이를 때만 허용한다.
- 미래 일정만 삭제한다. 참석 응답은 연쇄 삭제하고 출석·활동 기록이 있으면 거부한다.

세부 데이터 계약은 `erd.md`, API·오류 계약은 `api.md`, 삭제·마감의 파트 간 경계는 [`schedule-deletion-deadline-design.md`](schedule-deletion-deadline-design.md)를 따른다.

## 작업 흐름

1. `develop`을 최신화하고 `feature/part3-<topic>` 또는 `fix/part3-<problem>` 브랜치를 만든다.
2. 이 문서, `context.md`, 관련 코드·문서를 읽고 실제 Git 상태와 대조한다.
3. 완료 조건을 테스트 사례로 먼저 정의하고 필요한 최소 범위를 구현한다.
4. API나 데이터 계약이 바뀌면 같은 작업에서 `api.md` 또는 `erd.md`를 갱신한다.
5. 다른 파트 변경은 협업 원칙에 따라 분리하거나 명시한다.
6. 관련 테스트, 전체 테스트, `spotlessCheck`를 실행한다.
7. 변경 범위를 확인하고 논리 단위로 커밋한 뒤 `develop` 대상 PR을 만든다.

```powershell
.\gradlew.bat test
.\gradlew.bat spotlessCheck
```

브랜치 하나에는 목적 하나만 둔다. 문서 작업은 `feature/part3-docs-<topic>`, 리팩터링은 `feature/part3-refactor-<target>`, 테스트는 `feature/part3-test-<target>`을 사용한다. 커밋 형식과 타입은 README의 `<type>: <한글 설명>`을 따른다.

## 구현 원칙

### API와 Controller

- `/api`와 복수형 리소스 경로를 사용하고 임의로 `/api/v1`을 도입하지 않는다.
- 요청은 request DTO와 `@Valid`, 응답은 response DTO와 공통 `ApiResponse`를 사용한다.
- 인증 사용자는 `@AuthenticationPrincipal AuthenticatedMember`로 받고 ID만 전달한다.
- Controller는 입력 변환·인증 확인·서비스 호출·HTTP 응답만 담당한다.
- 생성 `201`, 조회·수정 `200`, 본문 없는 삭제 `204`를 명시한다.
- Entity를 노출하거나 비즈니스 로직·Repository 호출·반복 `try/catch`를 두지 않는다.

### Service와 Repository

- Service가 유스케이스, 권한, 상태 전이와 트랜잭션을 조정한다.
- 조회는 읽기 전용 트랜잭션을 사용하고 다른 파트는 공개 인터페이스로만 호출한다.
- Repository는 Part3 Entity 저장·조회만 맡고 그룹 종속 조회에는 그룹 ID를 포함한다.
- 비즈니스 규칙이나 응답 조립을 Repository에 넣지 않는다.

### DTO와 Entity

- 입력은 `dto/request`, 출력은 `dto/response`로 나누고 유스케이스에 필요한 필드만 둔다.
- 형식·필수값은 DTO, 권한·상태·현재 시각 규칙은 Service가 검증한다.
- Entity는 공개 setter 대신 의미 있는 생성·변경 메서드를 제공한다.
- 외부 도메인 Entity 대신 식별자나 합의된 포트를 사용한다.

## 테스트와 문서

- Controller: 경로, 상태, 인증 전달, 요청 검증, 응답 계약
- Service: 권한, 상태, 시간 경계, 정상·실패 유스케이스
- Repository: 그룹 범위 조회, 정렬, FK·UNIQUE 제약
- 버그 수정: 실패 재현 테스트를 먼저 추가
- 파트 간 계약 변경: 양쪽 경계 테스트와 PR 설명을 보완

API는 `api.md`, 데이터는 `erd.md`, Part3 작업 규칙은 이 문서, 현재 진행 상태는 `context.md`에만 상세히 기록한다. 완료된 구현 계획과 세션별 작업 일지는 장기 보관하지 않는다.
