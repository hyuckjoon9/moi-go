# 모집글·지원 ↔ 스터디 그룹 연동 설계

## 목적과 배경

기존에는 리더가 지원자를 모두 승인한 뒤 별도의 "그룹 확정"(`confirmGroup()`) 호출로 스터디 그룹과 그룹원을 한 번에 생성했다. 이 방식은 그룹 생성 시점이 모집 종료 이후로 미뤄져, 모집 진행 중에는 그룹이 전혀 존재하지 않는다는 문제가 있었다. 이를 Part3가 제공하는 `StudyGroupProvisioningPort` 계약에 맞춰, 모집글 작성 시점에 그룹을 즉시 만들고 지원이 승인될 때마다 그룹원을 순차적으로 추가하는 방식으로 전환했다.

이 설계는 HTTP API가 아닌 Part2 → Part3 내부 계약이며, Part3 쪽 포트 정의는 [`api.md`](api.md)가 아니라 Part3 `group-creation-design.md`를 기준으로 한다.

## 사용하는 포트

```java
public interface StudyGroupProvisioningPort {
    Long createGroup(CreateStudyGroupCommand command);
    Long addMember(AddStudyGroupMemberCommand command);
    Long endGroup(Long postId);
}
```

Part2는 이 인터페이스만 의존하며, `study` 패키지의 구현체(`StudyGroupProvisioningService`)나 Entity(`StudyGroup`, `GroupMember`)를 직접 참조하지 않는다.

## 호출 지점

| 호출 지점 | 위치 | 시점 |
| --- | --- | --- |
| `createGroup` | `RecruitmentService.create()` | 모집글 저장 직후, 같은 트랜잭션 |
| `addMember` | `JoinApplicationService.approve()` | 지원서 상태를 `APPROVED`로 바꾼 직후, 같은 트랜잭션 |
| `endGroup` | `RecruitmentService.end()` | 모집글 상태를 `ENDED`로 바꾼 직후, 같은 트랜잭션 |

세 메서드 모두 Part3에서 `@Transactional(propagation = Propagation.REQUIRED)`로 선언돼 있어 Part2의 트랜잭션에 참여한다. 즉 `addMember()`가 예외를 던지면 `approve()`에서의 지원 상태 변경(`application.approve()`)도 함께 롤백된다.

## `CreateStudyGroupCommand` 사용 방식

`CreateStudyGroupCommand`는 `postId`, `groupName`, `leaderUserId`, `approvedUserIds` 4개 필드를 갖는다. Part2는 모집글 생성 시점에는 승인된 지원자가 아직 없으므로 `approvedUserIds`에 항상 빈 리스트(`List.of()`)를 전달한다.

```java
studyGroupProvisioningPort.createGroup(
    new CreateStudyGroupCommand(post.getId(), post.getTitle(), leaderId, List.of()));
```

Part3의 실제 구현(`StudyGroupProvisioningService.createGroup()`)은 `approvedUserIds`를 사용하지 않고 `postId`, `groupName`, `leaderUserId`만으로 그룹과 리더 멤버를 생성한다. 이후 멤버 추가는 전부 `addMember()` 건별 호출로 이뤄진다.

## 폐기된 방식과의 차이

| 구분 | 기존(`confirmGroup()`) | 현재 |
| --- | --- | --- |
| 그룹 생성 시점 | 리더가 승인을 모두 마친 뒤 별도 호출 | 모집글 작성 즉시 |
| 그룹원 등록 방식 | 승인된 지원자 전체를 한 번에 일괄 등록 | 지원 승인 건별로 즉시 추가 |
| Part2 호출 메서드 | 별도 `StudyGroupCreationService.create()` 1회 | `createGroup`(작성 시) + `addMember`(승인 건별) + `endGroup`(종료 시) |
| 모집글 상태 연동 | `confirmGroup()`이 `post.activate()`를 호출해야 했으나 누락돼 재지원이 가능한 버그가 있었음 | 그룹 생성이 작성 시점으로 이동하며 해당 버그 재현 경로가 사라짐 |

## 오류 처리

`addMember()`는 다음 조건에서 예외를 던지며, Part2는 이를 그대로 전파해 승인 자체를 실패시킨다.

| Part3 오류 코드 | 조건 | Part2에서의 영향 |
| --- | --- | --- |
| `GROUP_NOT_FOUND` | 해당 `postId`의 그룹이 없음(정상 흐름에서는 발생하지 않아야 함) | 승인 실패, 지원 상태 롤백 |
| `GROUP_MEMBER_ADD_NOT_ALLOWED` | 그룹이 이미 `ENDED`(과거에는 `GROUP_ENDED`를 재사용했으나, 일정 관리 실패와 메시지가 겹쳐서 Part3가 전용 코드로 분리함 — "종료된 그룹에는 새 그룹원을 추가할 수 없습니다.") | 승인 실패, 지원 상태 롤백 |
| `WITHDRAWN_GROUP_MEMBER` | 지원자가 과거 탈퇴 이력이 있는 그룹원 | 승인 실패, 지원 상태 롤백 |

이미 활성 멤버인 경우(같은 사용자를 중복 승인 시도하는 등)는 멱등하게 성공 처리된다(다만 Part2 쪽에서는 `application.getStatus() != PENDING` 체크가 먼저 걸려 `APPLICATION_ALREADY_PROCESSED`로 막히므로 실제로는 도달하지 않는 경로다).

## 파트 간 책임 경계

- Part2(`recruitment`, `application`)는 `study` 패키지 내부 파일을 생성·수정하지 않는다. 계약 변경이 필요하면 Part3에 요청하고 구현을 기다린다.
- Part3(`study`)는 `recruitment`, `application` 패키지의 Entity·Repository를 직접 참조하지 않고 `postId`, `userId` 식별자만 받는다.
- 그룹이 연결된 모집글을 삭제할 때 발생하는 FK 제약(`study_groups.post_id RESTRICT`)의 최종 처리 정책(항상 삭제 금지 vs. 그룹도 함께 정리하는 별도 유스케이스 추가)은 아직 확정되지 않았다. 확정 시 이 문서와 `feature-spec.md`, `erd.md`를 함께 갱신한다.

## 검증 요점

- 모집글 작성 시 그룹이 정확히 한 번 생성되고 리더가 `LEADER`로 등록되는지(`RecruitmentServiceTest.create_success`).
- 지원 승인 시 `addMember`가 올바른 `postId`, `userId`로 호출되는지(`JoinApplicationServiceTest.approve_success`).
- 스터디 종료 시 `endGroup`이 호출되고 모집글·그룹 상태가 함께 `ENDED`로 바뀌는지(`RecruitmentServiceTest.end_success`, DB 직접 조회로 확인).
- `addMember` 실패 시 지원 상태 변경이 롤백되는지(현재 단위 테스트에서는 mock 기반으로만 검증됨 — 실제 트랜잭션 롤백을 확인하는 통합 테스트 추가는 미완료).
