# Back Office 통합 가이드

## 목적

이 문서는 Back Office 변경을 기존 기능 또는 최신 `develop`과 합칠 때 지켜야 할 코드·DB·화면
계약을 정리한다. Back Office는 일반 서비스와 같은 저장소를 공유하지만, 관리자 전용 조회와
운영 조치를 별도 `admin` 모듈에서 제공한다.

## 코드 통합 계약

### 회원 관리

- 관리자 상태 변경은 `AdminMemberService -> MemberAdministrationPort` 경로만 사용한다.
  관리자 서비스가 `Member` 엔티티를 직접 수정하지 않는다.
- `Member.changeStatus(MemberStatus)`와 `MemberAdministrationService`는 유지한다.
- 변경 대상은 일반 회원(`USER`)의 `ACTIVE`와 `SUSPENDED`뿐이다. 관리자, 탈퇴 회원, 자기 자신은
  변경할 수 없다.
- 실제 정지에서는 refresh token을 폐기하고, 실제 상태 변경에서만 `AdminAuditLog`를 저장한다.

### 모집글 노출 관리

- `RecruitmentStatus`는 모집·그룹 진행 흐름이고, `RecruitmentVisibility`는 운영 노출 제어다.
  두 상태를 하나로 합치지 않는다.
- 일반 사용자 목록·상세·지원은 `VISIBLE` 모집글만 허용한다. 관리자는 `VISIBLE`과 `HIDDEN`을
  모두 검색·상세 조회한다.
- `RecruitmentPost.visibility`, `RecruitmentAdministrationPort`,
  `RecruitmentAdministrationService`를 유지한다.
- 숨김·복구는 `AdminRecruitmentService`가 공개 관리 포트를 호출하고, 실제 변경에만
  `RECRUITMENT_HIDDEN` 또는 `RECRUITMENT_RESTORED` 이력을 남긴다.

### 충돌 처리

- 회원 상태 변경 요청은 `expectedStatus`, 모집글 노출 변경 요청은 `expectedVisibility`를 보낸다.
- 서버의 현재 상태와 기대 상태가 다르면 `ADMIN_OPERATION_CONFLICT`(HTTP 409)를 반환한다.
- 이미 목표 상태인 요청은 멱등 성공으로 처리하고 새 운영 이력을 남기지 않는다.
- `src/main/resources/static/js/api.js`는 실패 응답에 `error.status`를 설정해야 한다. Back Office
  화면은 409일 때 목록·상세를 다시 읽어 최신 상태를 보여 준다.

### 읽기 전용 운영 조회

- 그룹·일정·출석·활동·운영 이력 조회는 `admin` 모듈의 JDBC projection만 사용한다.
- 조회 API는 해당 도메인 Entity를 변경하거나 관리 포트를 호출하지 않는다.
- 응답에는 인증 비밀값, 비밀번호, refresh token을 포함하지 않는다.

## 개발 DB 기준

개발 DB를 처음 구성하거나 초기화할 때는 다음 파일만 실행한다.

```bash
mysql -u <user> -p moigo < sql/moigo_schema_seed.sql
```

이 스크립트는 관련 테이블을 삭제 후 다시 생성하므로 개발 DB 전용이다. Back Office가 사용하는
핵심 구조는 다음과 같다.

| 대상 | 필수 구조 | 사용 위치 |
| --- | --- | --- |
| `users` | `role`, `status` | 관리자 권한·회원 상태 변경 |
| `recruitment_posts` | `visibility` 기본값 `VISIBLE`, `VISIBLE/HIDDEN` 제약 | 일반 사용자 노출 필터·관리자 숨김 |
| `admin_audit_logs` | 운영자·대상·전후 상태·사유·시각 | 회원·모집글 운영 이력 |
| `study_groups`, `study_schedules` | 그룹 상태·일정·그룹 연결 | 관리자 읽기 전용 조회 |
| `attendance_records`, `activity_records` | 일정별 출석·활동 기록 | 관리자 읽기 전용 조회 |

시드 실행 후 아래 검증 쿼리의 결과가 0이면 노출 상태 데이터가 정상이다.

```sql
SELECT COUNT(*) AS invalid_visibility_count
FROM recruitment_posts
WHERE visibility IS NULL OR visibility NOT IN ('VISIBLE', 'HIDDEN');
```

배포 전에는 별도 운영 DB 마이그레이션 정책을 결정한다. 현재 개발 단계에서는 migration SQL 파일을
추가하거나 자동 실행 도구를 도입하지 않는다.

## 병합 체크리스트

1. `admin/**`, Back Office 정적 자산, 관리 포트와 읽기 전용 조회가 삭제되지 않았는지 확인한다.
2. `RecruitmentVisibility`와 일반 사용자 `VISIBLE` 필터가 함께 남아 있는지 확인한다.
3. `api.js`의 `error.status`와 Back Office의 409 재조회 흐름을 확인한다.
4. `sql/moigo_schema_seed.sql`에 `visibility`와 `admin_audit_logs`가 있는지 확인한다.
5. `SPRING_DATASOURCE_USERNAME=<user> SPRING_DATASOURCE_PASSWORD=<password> ./gradlew test`를
   실행한다.
