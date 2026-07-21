# 그룹·그룹원 영속성 설계

## 매핑 경계

- `StudyGroup.postId`와 `GroupMember.userId`는 외부 Entity가 아닌 `Long` 식별자다.
- Part3 내부의 `GroupMember.studyGroup`만 지연 로딩 `ManyToOne`으로 매핑한다.
- 운영 DB가 외부 FK를 보장하고, 외부 식별자 검증은 합의된 공개 서비스에서 수행한다.

컬럼·Enum·UNIQUE 제약은 [`erd.md`](erd.md)를 기준으로 한다.

## Entity

- `StudyGroup`: 기본 `ACTIVE`, 상태 변경은 `end()`로 제한한다.
- `GroupMember`: 기본 `ACTIVE`, 역할 변경은 `changeRole()`, 탈퇴는 `withdraw()`로 표현한다.
- 공개 setter를 두지 않고 생성 시 필수 식별자와 이름을 검증한다.

## Repository와 검증

- `StudyGroupRepository`: 모집글별 단건 조회와 존재 확인
- `GroupMemberRepository`: 그룹·사용자 단건, 그룹원 목록, 사용자·상태별 목록 조회
- Spring Data 파생 쿼리 이름에 조회 조건을 드러낸다.

영속성 테스트는 정상 저장, Enum 문자열, 모집글·그룹원 중복 제약, 각 조회 조건과 상태 변경을 검증한다.
