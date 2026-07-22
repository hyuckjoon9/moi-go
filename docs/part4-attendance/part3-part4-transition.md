Part3 쪽 그룹 전체 출석률 조회 권한 포트 추가했습니다.

```java
StudyGroupAttendanceRatePolicy getAttendanceRatePolicy(
    Long groupId,
    Long requesterId
);
```
반환값의 canViewAllAttendanceRates가 true인 경우는 아래뿐입니다.
- 그룹 상태: ACTIVE
- 요청자 그룹원 상태: ACTIVE
- 요청자 역할: LEADER 또는 MANAGER

Part4의 getGroupAttendanceRates()에서 기존 validateGroupManager() 권한 검증은 이 포트 호출로 교체해 주세요.
```java
StudyGroupAttendanceRatePolicy policy =
    studyGroupAttendanceRatePolicyReader.getAttendanceRatePolicy(groupId, requesterId);

if (!policy.canViewAllAttendanceRates()) {
    throw new BusinessException(ErrorCode.ATTENDANCE_MANAGEMENT_FORBIDDEN);
}
```
그룹 없음·비그룹원·탈퇴 그룹원은 포트가 기존 Part3 오류로 처리합니다. 따라서 권한 판단을 위해 GroupRole이나 GroupMemberRepository를 직접 조회할 필요는 없습니다.
개인 출석률은 현재처럼 인증 사용자와 path userId가 같은지 검증하면 되고, 그룹 전체 출석률은 반드시 groupId의 일정만 집계해 주세요.

단, Part4가 “그룹원별 목록”을 만들기 위해 `GroupMemberRepository`에서 활성 그룹원을 조회하는 부분은 별도입니다. 그것까지 Part3 경계로 완전히 분리하려면 Part3에 **활성 그룹원 목록 조회 포트**를 추가해야 합니다.