# moi-go 테스트 가이드

> 대상 독자: QA·PM·백오피스 담당자 등 API를 직접 호출하며 테스트하는 모든 사람
> 함께 쓰는 파일: [`moigo_schema_seed.sql`](../../sql/moigo_schema_seed.sql) — 이 문서에서 설명하는 계정·데이터가 전부 이 파일 기준이다.

## 1. 목적

`main` 브랜치에 DB 스키마와 샘플 데이터가 없어 로컬에서 바로 테스트하기 어렵다는 요청에 따라, 스키마 생성부터 샘플 데이터 삽입까지 한 번에 끝나는 SQL 파일과 그 데이터를 기준으로 한 테스트 시나리오를 정리한다.

## 2. 테스트 환경 준비

### 2.1 DB 스키마 + 샘플 데이터 적용

```powershell
mysql -u <계정> -p moigo < sql/moigo_schema_seed.sql
```

- 파일 맨 위에서 기존 테이블을 `DROP TABLE IF EXISTS`로 정리한 뒤 11개 테이블을 `CREATE TABLE`로 새로 만들고, 이어서 샘플 데이터를 `INSERT`한다. 재실행해도 항상 같은 상태로 초기화된다.
- 실행 전 `moigo` 데이터베이스가 존재해야 한다(`CREATE DATABASE moigo;`는 파일에 포함되어 있지 않음).
- `application-local.example.properties`를 복사한 `application-local.properties`의 `spring.datasource.url`이 이 DB(`moigo`)를 가리키는지 확인한다. `spring.jpa.hibernate.ddl-auto=validate`라 Hibernate가 스키마를 새로 만들어주지 않으므로, 반드시 이 SQL을 먼저 실행해야 앱이 정상 기동한다.

### 2.2 앱 실행

Windows:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

macOS / Linux:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 3. 테스트 계정 목록

모든 일반 계정 비밀번호는 `Password1!`, admin 계정은 `Admin1234!`.

| id | 이메일 | 닉네임 | role | 용도 |
| --- | --- | --- | --- | --- |
| 1 | `leader@moigo.test` | 모이고리더 | USER | Part2 모집글 1(`ACTIVE`) 리더 |
| 2 | `member@moigo.test` | 모이고멤버 | USER | 모집글 1 승인 멤버, 모집글 4(종료) 과거 멤버 — 그룹 이력 조회 테스트용 |
| 3 | `sooyeon@moigo.test` | 김수연 | USER | 모집글 2(`RECRUITING`) 리더 |
| 4 | `minjun@moigo.test` | 이민준 | USER | 모집글 1 지원 대기(`PENDING`) |
| 5 | `hyejin@moigo.test` | 박혜진 | USER | 모집글 1 지원 거절(`REJECTED`) |
| 6 | `jiho@moigo.test` | 최지호 | USER | 모집글 2 승인 멤버 |
| 7 | `yuna@moigo.test` | 정유나 | USER | 모집글 2 지원 취소(`CANCELLED`) |
| 8 | `taehyun@moigo.test` | 강태현 | USER | 모집글 3(`CLOSED`) 리더 |
| 9 | `dohyun@moigo.test` | 한도현 | USER | 모집글 3 승인 후 그룹 탈퇴(`WITHDRAWN`) |
| 10 | `eunji@moigo.test` | 오은지 | USER | 모집글 4(`ENDED`) 리더, 그룹도 `ENDED` |
| 11 | `withdrawn@moigo.test` | 탈퇴한회원 | USER (status=`WITHDRAWN`) | 탈퇴 회원 로그인 차단 테스트용 |
| 12 | `admin@moigo.test` | 운영자 | **ADMIN** | 백오피스 로그인용 |

## 4. Part별 API 빠른 참조

상세 요청/응답 예시는 각 파트 문서를 기준으로 한다.

| Part | 문서 | 대표 엔드포인트 |
| --- | --- | --- |
| Part1 인증·회원 | <a href="../part1-auth/api.md" target="_blank">API 문서</a> | `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/reissue` |
| Part2 모집글·지원 | <a href="../part2-recruitment/api.md" target="_blank">API 문서</a> | `POST /api/recruitment-posts`, `PATCH .../applications/{id}/approve` 등 |
| Part3 그룹·일정 | <a href="../part3-group/api.md" target="_blank">API 문서</a> | `GET /api/groups/me`, `GET /api/groups/{groupId}`, `.../schedules` |
| Part4 출석·활동 | <a href="../part4-attendance/attendance-api.md" target="_blank">출석 API</a>, <a href="../part4-attendance/activity-api.md" target="_blank">활동 API</a> | 출석 응답·체크, 활동 기록·리뷰 |

인증이 필요한 모든 요청은 `POST /api/auth/login`으로 받은 `accessToken`을 `Authorization: Bearer <token>` 헤더에 담아 호출한다(로그인·회원가입·토큰 재발급 제외).

## 5. 시나리오 체크리스트

### 5.0 Part2 Postman 통합 테스트 결과 (11단계, 완료)

`StudyGroupProvisioningPort` 연동을 포함한 Part2 전체 흐름을 이 데이터 기준으로 이미 한 차례 완주한 결과다. 아래 5.2의 체크리스트는 이 11단계와 별개로 회귀 확인용으로 남겨둔 것이다.

1. 모집글 작성 → 그룹 자동생성(LEADER 등록) — 통과
2. 지원 등록 — 통과
3. 지원자 목록 조회 — 통과
4. 지원 승인 → 그룹원 자동 추가 — 통과 (DB로 최종 확인)
5. 지원 거절 — 통과
6. 예외 케이스 — 이미 처리된 지원 재처리 시 409, 리더 아닌 사람 접근 시 403 — 통과
7. 모집글 수정(update) — 통과
8. 모집 마감(close) — 통과
9. 스터디 종료(end) → 연결된 그룹도 ENDED로 반영 — 통과 (DB로 최종 확인)
10. 삭제(delete) + 그룹 연결된 경우 409 처리 — 확인
11. 내 신청 목록 조회(상태 필터 포함) — 확인 중

### 5.1 로그인·권한

- [ ] `leader@moigo.test` / `Password1!`로 로그인 → 200, `accessToken` 발급 확인
- [ ] `withdrawn@moigo.test` / `Password1!`로 로그인 → 401(탈퇴 회원) 확인
- [ ] `admin@moigo.test` / `Admin1234!`로 로그인 → 200, 발급된 토큰으로 백오피스 화면(`/backoffice/**`) 접근 확인

### 5.1.1 Back Office 대시보드

- [ ] 비로그인 상태에서 `/backoffice/index.html` 진입 → 로그인 화면으로 이동
- [ ] `leader@moigo.test` 로그인 후 Back Office 진입 → 권한 없음 표시, 관리자 API 미호출
- [ ] `admin@moigo.test` 로그인 후 진입 → 회원·모집글·그룹 집계 표시
- [ ] ADMIN 토큰으로 `GET /api/admin/dashboard` → 200과 `ApiResponse` 확인
- [ ] USER 토큰으로 같은 API 호출 → 403과 `관리자 권한이 필요합니다.` 확인

### 5.2 Part2 모집글·지원

- [ ] 모집글 목록(`GET /api/recruitment-posts`)에서 4건(RECRUITING/CLOSED/ACTIVE/ENDED) 모두 노출되는지 확인
- [ ] `minjun`으로 로그인해 모집글 1 지원 목록 조회 시 자신의 `PENDING` 건이 보이는지(`GET /api/join-applications/me`)
- [ ] `leader`로 로그인해 모집글 1 지원자 목록에서 `minjun`(PENDING)을 승인 → `APPROVED` 전환 + Part3 그룹에 멤버 추가되는지 확인
- [ ] 모집글 2(`RECRUITING`)에 새 계정으로 지원 → 성공, 동일 계정 재지원 시 `DUPLICATE_APPLICATION`(409) 확인
- [ ] 모집글 3(`CLOSED`)에 지원 시도 → `RECRUITMENT_CLOSED`(400) 확인
- [ ] 모집글이 연결된 그룹이 있는 상태에서 삭제 시도 → `RECRUITMENT_DELETE_NOT_ALLOWED`(409) 확인

### 5.3 Part3 그룹·일정

- [ ] `member@moigo.test`로 `GET /api/groups/me` 호출 → 그룹 1(ACTIVE)과 그룹 4(ENDED) 둘 다 응답에 포함되는지 확인(과거 이슈였던 "ENDED 그룹 미표시" 회귀 확인용)
- [ ] `dohyun@moigo.test`(그룹 3에서 WITHDRAWN)로 그룹 3 일정 조회 시도 → 접근 거부되는지 확인
- [ ] 그룹 1 일정 목록(`GET /api/groups/1/schedules`) 조회 → 2건(1주차/2주차) 확인

### 5.4 Part4 출석·활동

- [ ] 그룹 3 리더(`taehyun`)로 지난 일정(3주차)의 출석 현황 조회 → `dohyun` PRESENT 기록 확인
- [ ] 그룹 4(ENDED) 마지막 일정의 활동 기록·리뷰 조회 → 종료된 그룹도 과거 기록 열람 가능한지 확인


## 6. 데이터 초기화

테스트 중 데이터가 꼬였다면 `moigo_schema_seed.sql`을 다시 실행하면 된다(맨 위 `DROP TABLE IF EXISTS`가 전체를 정리하고 처음 상태로 되돌린다).
