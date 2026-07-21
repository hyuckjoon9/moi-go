# 그룹 생성 설계

## 목적과 계약

Part2의 모집 결과로 그룹과 초기 그룹원을 한 번만 생성한다. HTTP API가 아닌 내부 계약이며 상세 입력은 [`api.md`](api.md)를 따른다.

`StudyGroupCreationService.create(CreateStudyGroupCommand)`는 생성됐거나 기존인 그룹 ID를 반환한다. 같은 `postId`로 내용이 다른 재요청이 와도 최초 결과를 유지하며 재동기화는 별도 유스케이스로 다룬다.

## 책임

- `CreateStudyGroupCommand`: 필수값과 그룹명을 검증하고 승인 회원 목록을 방어적으로 복사한다.
- `StudyGroupCreationService`: 기존 그룹을 먼저 조회하고 신규 저장 또는 동시 생성 충돌을 조정한다.
- `StudyGroupCreationWriter`: 별도 Spring Bean의 트랜잭션에서 그룹과 초기 그룹원을 함께 저장한다.

Writer는 모집장을 `LEADER`로 먼저 추가하고, 승인 회원의 순서를 유지하면서 중복과 모집장을 제거해 `MEMBER`로 저장한다. 중간 실패 시 전부 롤백한다.

## 멱등성과 오류

일반 재요청은 `postId` 선조회로 처리하고, 동시 요청은 DB의 `study_groups.post_id` UNIQUE를 최종 경계로 삼는다. Writer 트랜잭션이 유니크 충돌로 끝난 뒤 Service가 기존 그룹을 재조회한다. 기존 그룹이 없으면 FK 등 다른 무결성 오류일 수 있으므로 원래 예외를 전파한다.

- 식별자·그룹명·승인 목록이 없거나 목록에 `null`이 있으면 `IllegalArgumentException`이다.
- 빈 승인 목록은 허용한다.
- 승인 회원 중복과 모집장 중복은 정규화하며 오류로 보지 않는다.
- 외부 모집글·사용자 존재는 Part2가 보장하고 Part3는 외부 Repository를 조회하지 않는다.

## 검증 요점

- 기존 요청은 Writer를 호출하지 않고 같은 ID를 반환한다.
- 신규·빈 목록·중복 목록이 올바른 역할 집합을 만든다.
- 그룹원 저장 실패는 그룹까지 롤백한다.
- 병렬 요청은 그룹과 정규화된 그룹원 집합을 하나만 남긴다.
