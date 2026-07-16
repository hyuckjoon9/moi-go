# 그룹·그룹원 영속성 설계

## 목표

`study_groups`와 `group_members`를 문서화된 스키마 제약에 맞게 저장하고 조회할 수 있는 Part3 영속성 기반을 만든다. 이번 범위에는 그룹 생성 서비스와 HTTP API를 포함하지 않는다.

## 매핑 경계

- Part3 외부 FK인 `study_groups.post_id`와 `group_members.user_id`는 `Long` 식별자로 매핑한다.
- Part3가 소유하는 그룹과 그룹원 관계만 `GroupMember`에서 지연 로딩 `ManyToOne`으로 매핑한다.
- 이 방식은 빈 스켈레톤 상태인 Part1·Part2 엔티티 구현에 결합하지 않으면서 운영 DB의 기존 FK를 그대로 사용할 수 있다.
- 외부 FK의 실제 존재 여부는 운영 스키마가 보장하며, Part3는 이후 합의된 공개 서비스 경계에서 식별자를 검증한다.

## 엔티티

### `StudyGroup`

- 식별자, 모집글 식별자, 이름, 상태, 생성 시각을 가진다.
- 상태는 `ACTIVE`, `ENDED`만 허용한다.
- 생성 시 기본 상태는 `ACTIVE`이며 공개 setter를 제공하지 않는다.
- 의미 있는 상태 변경은 `end()` 메서드로 제한한다.

### `GroupMember`

- 식별자, 그룹, 사용자 식별자, 역할, 상태, 가입 시각을 가진다.
- 역할은 `LEADER`, `MANAGER`, `MEMBER`다.
- 상태는 새 `GroupMemberStatus` Enum의 `ACTIVE`, `WITHDRAWN`으로 표현한다.
- 생성 시 기본 상태는 `ACTIVE`이며 `changeRole()`과 `withdraw()`로만 변경한다.

## Repository

- `StudyGroupRepository`: 모집글 식별자 단건 조회와 존재 여부 확인
- `GroupMemberRepository`: 그룹·사용자 단건 조회, 그룹원 목록 조회, 사용자의 상태별 그룹원 목록 조회
- Spring Data JPA 파생 쿼리를 사용해 조회 조건을 메서드 이름에 드러낸다.

## 오류와 제약

- 모집글 식별자는 유일해야 한다.
- 그룹 안에서 사용자 식별자는 유일해야 한다.
- 필수 필드와 문자열 길이는 JPA 컬럼 정의에 반영한다.
- 엔티티 생성자는 null 식별자와 빈 이름을 거부한다.
- 외부 사용자·모집글 존재 여부 검증은 이번 영속성 범위에서 수행하지 않는다.

## 테스트

- `@DataJpaTest`와 H2 MySQL 모드를 사용한다.
- 정상 저장, Enum 문자열 저장, 모집글 식별자 중복, 그룹원 중복을 검증한다.
- 모집글별 그룹, 그룹·사용자별 그룹원, 사용자·상태별 그룹원 조회를 검증한다.
- 상태 변경 메서드는 단위 테스트로 검증한다.

