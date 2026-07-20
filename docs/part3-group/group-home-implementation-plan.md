# Part3 그룹 홈 구현 계획

**목표:** 인증된 활성 그룹원이 그룹 기본 정보와 활성 그룹원 목록을 조회할 수 있게 한다.

**구조:** `StudyGroupController`가 인증 사용자를 받고 읽기 전용 `StudyGroupService`에 위임한다. 서비스는 그룹·그룹원 접근 규칙을 적용한 뒤 정렬된 활성 그룹원 조회 결과를 응답 DTO로 변환한다.

**기술:** Java 21, Spring Boot 4.1, Spring Data JPA, Spring Security, JUnit 5, Mockito.

## 전역 제약

- 엔드포인트는 `GET /api/groups/{groupId}`다.
- `AuthenticatedMember.id()`만 사용하며 인증 내부와 다른 파트 Repository는 수정하지 않는다.
- 활성 그룹원은 `LEADER`, `MANAGER`, `MEMBER`, `joinedAt`, `userId` 순으로 정렬한다.
- 활성 그룹원은 `ENDED` 그룹도 조회할 수 있고, 저장된 그룹 상태를 그대로 반환한다.
- `global/exception/ErrorCode.java`에는 `GROUP_NOT_FOUND`, `GROUP_ACCESS_DENIED`, `WITHDRAWN_GROUP_MEMBER`만 추가한다.
- 기존 미커밋 `api.md`, `context.md` 변경은 보존한다.

## 작업 1: 활성 그룹원 정렬 조회

**변경 파일:**

- `src/main/java/com/mycom/myapp/study/repository/GroupMemberRepository.java`
- `src/test/java/com/mycom/myapp/study/repository/GroupMemberRepositoryTest.java`

1. 활성 상태 그룹원만 역할·가입일·사용자 ID 순으로 가져오는 Repository 메서드를 추가한다.
2. 탈퇴 그룹원 제외와 역할·동일 가입 시각의 사용자 ID 정렬을 데이터 JPA 테스트로 검증한다.

## 작업 2: 그룹 홈 서비스와 응답 DTO

**변경 파일:**

- `src/main/java/com/mycom/myapp/study/service/StudyGroupService.java`
- `src/main/java/com/mycom/myapp/study/dto/response/StudyGroupHomeResponse.java`
- `src/main/java/com/mycom/myapp/study/dto/response/GroupMemberSummaryResponse.java`
- `src/main/java/com/mycom/myapp/global/exception/ErrorCode.java`
- `src/test/java/com/mycom/myapp/study/service/StudyGroupHomeContractTest.java`
- `src/test/java/com/mycom/myapp/study/service/StudyGroupServiceTest.java`

1. 읽기 전용 서비스에서 그룹을 조회한다.
2. 그룹 없음·비회원·탈퇴 회원을 각각 합의된 오류 코드로 구분한다.
3. 정상 조회와 종료 그룹 조회 응답을 조립한다.
4. 성공, 종료 그룹, 세 접근 오류를 서비스 테스트로 검증한다.

## 작업 3: HTTP 컨트롤러 계약

**변경 파일:**

- `src/main/java/com/mycom/myapp/study/controller/StudyGroupController.java`
- `src/test/java/com/mycom/myapp/study/controller/StudyGroupControllerTest.java`

1. `@AuthenticationPrincipal AuthenticatedMember`에서 사용자 ID를 전달한다.
2. principal이 없으면 기존 공통 `UNAUTHORIZED` 계약을 사용한다.
3. 서비스 결과를 `ApiResponse.success(...)`로 감싸 반환한다.
4. 인증 사용자 전달 및 성공 응답, principal 부재를 테스트한다.

## 검증

1. 그룹 홈 관련 Repository·Service·Controller 테스트를 실행한다.
2. 전체 `test`를 실행한다.
3. `spotlessCheck`를 실행한다.
4. `git diff --check`, `git status --short`로 기존 문서 변경 보존 및 변경 범위를 확인한다.
