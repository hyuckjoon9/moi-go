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

@DataJpaTest
class GroupMemberRepositoryTest {

    @Autowired private StudyGroupRepository studyGroupRepository;

    @Autowired private GroupMemberRepository groupMemberRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void findsMembersWithinGroupBoundary() {
        StudyGroup group = studyGroupRepository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));
        GroupMember member =
                groupMemberRepository.saveAndFlush(GroupMember.join(group, 20L, GroupRole.MEMBER));

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

        assertThat(storedRole).isEqualTo("MANAGER");
        assertThat(storedStatus).isEqualTo("ACTIVE");
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
}
