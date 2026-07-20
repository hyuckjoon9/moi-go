package com.mycom.myapp.study.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupMemberStatus;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class GroupMemberRepositoryTest {

    @Autowired private StudyGroupRepository studyGroupRepository;

    @Autowired private GroupMemberRepository groupMemberRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void findsMembersWithinGroupBoundary() {
        StudyGroup group = studyGroupRepository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));
        StudyGroup otherGroup = studyGroupRepository.saveAndFlush(StudyGroup.create(11L, "다른 스터디"));
        GroupMember member =
                groupMemberRepository.saveAndFlush(GroupMember.join(group, 20L, GroupRole.MEMBER));
        groupMemberRepository.saveAndFlush(GroupMember.join(otherGroup, 20L, GroupRole.MEMBER));

        assertThat(groupMemberRepository.findByStudyGroupIdAndUserId(group.getId(), 20L))
                .contains(member);
        assertThat(groupMemberRepository.findAllByStudyGroupId(group.getId()))
                .containsExactly(member);
        assertThat(member.getJoinedAt()).isNotNull();
    }

    @Test
    void findsActiveMembershipsByUser() {
        StudyGroup first = studyGroupRepository.save(StudyGroup.create(10L, "첫 번째 그룹"));
        StudyGroup second = studyGroupRepository.save(StudyGroup.create(11L, "두 번째 그룹"));
        GroupMember active =
                groupMemberRepository.save(GroupMember.join(first, 20L, GroupRole.MEMBER));
        GroupMember withdrawn = GroupMember.join(second, 20L, GroupRole.MEMBER);
        withdrawn.withdraw();
        groupMemberRepository.saveAndFlush(withdrawn);

        assertThat(groupMemberRepository.findAllByUserIdAndStatus(20L, GroupMemberStatus.ACTIVE))
                .containsExactly(active);
    }

    @Test
    void storesRoleAndStatusAsStrings() {
        StudyGroup group = studyGroupRepository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));
        GroupMember member =
                groupMemberRepository.saveAndFlush(GroupMember.join(group, 20L, GroupRole.MANAGER));

        String storedRole =
                jdbcTemplate.queryForObject(
                        "select role from group_members where id = ?",
                        String.class,
                        member.getId());
        String storedStatus =
                jdbcTemplate.queryForObject(
                        "select status from group_members where id = ?",
                        String.class,
                        member.getId());
        String roleColumnType =
                jdbcTemplate.queryForObject(
                        """
                        select data_type
                        from information_schema.columns
                        where table_name = 'GROUP_MEMBERS' and column_name = 'ROLE'
                        """,
                        String.class);
        String statusColumnType =
                jdbcTemplate.queryForObject(
                        """
                        select data_type
                        from information_schema.columns
                        where table_name = 'GROUP_MEMBERS' and column_name = 'STATUS'
                        """,
                        String.class);

        assertThat(storedRole).isEqualTo("MANAGER");
        assertThat(storedStatus).isEqualTo("ACTIVE");
        assertThat(roleColumnType).isEqualTo("CHARACTER VARYING");
        assertThat(statusColumnType).isEqualTo("CHARACTER VARYING");
    }

    @Test
    void rejectsDuplicateUserWithinGroup() {
        StudyGroup group = studyGroupRepository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));
        groupMemberRepository.saveAndFlush(GroupMember.join(group, 20L, GroupRole.MEMBER));

        assertThatThrownBy(
                        () ->
                                groupMemberRepository.saveAndFlush(
                                        GroupMember.join(group, 20L, GroupRole.MANAGER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsOnlyActiveMembersInGroupHomeOrder() {
        StudyGroup group = studyGroupRepository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));
        GroupMember leader =
                groupMemberRepository.saveAndFlush(GroupMember.join(group, 1L, GroupRole.LEADER));
        GroupMember manager =
                groupMemberRepository.saveAndFlush(GroupMember.join(group, 2L, GroupRole.MANAGER));
        GroupMember laterMember =
                groupMemberRepository.saveAndFlush(GroupMember.join(group, 300L, GroupRole.MEMBER));
        GroupMember earlierMember =
                groupMemberRepository.saveAndFlush(GroupMember.join(group, 100L, GroupRole.MEMBER));
        GroupMember withdrawn = GroupMember.join(group, 4L, GroupRole.MEMBER);
        withdrawn.withdraw();
        groupMemberRepository.saveAndFlush(withdrawn);

        jdbcTemplate.update(
                "update group_members set joined_at = timestamp '2026-07-01 10:00:00' where id in (?, ?)",
                laterMember.getId(),
                earlierMember.getId());
        ReflectionTestUtils.setField(leader, "joinedAt", null);
        groupMemberRepository.flush();

        assertThat(
                        groupMemberRepository
                                .findAllByStudyGroupIdAndStatusOrderByRoleAscJoinedAtAscUserIdAsc(
                                        group.getId(), GroupMemberStatus.ACTIVE))
                .extracting(GroupMember::getUserId)
                .containsExactly(1L, 2L, 100L, 300L);
    }
}
