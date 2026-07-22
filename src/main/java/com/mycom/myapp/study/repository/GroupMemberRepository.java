package com.mycom.myapp.study.repository;

import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.entity.GroupStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByStudyGroupIdAndUserId(Long groupId, Long userId);

    List<GroupMember> findAllByStudyGroupId(Long groupId);

    List<GroupMember> findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
            Long groupId, GroupMemberStatus status);

    List<GroupMember> findAllByUserIdAndStatus(Long userId, GroupMemberStatus status);

    @EntityGraph(attributePaths = "studyGroup")
    List<GroupMember> findAllByUserIdAndStatusAndStudyGroupStatusOrderByJoinedAtDescIdDesc(
            Long userId, GroupMemberStatus status, GroupStatus groupStatus);
}
