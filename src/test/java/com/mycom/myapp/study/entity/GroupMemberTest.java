package com.mycom.myapp.study.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class GroupMemberTest {

    @Test
    void joinInitializesActiveMember() {
        StudyGroup group = StudyGroup.create(10L, "알고리즘 스터디");

        GroupMember member = GroupMember.join(group, 20L, GroupRole.MEMBER);

        assertThat(member.getStudyGroup()).isSameAs(group);
        assertThat(member.getUserId()).isEqualTo(20L);
        assertThat(member.getRole()).isEqualTo(GroupRole.MEMBER);
        assertThat(member.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
    }

    @Test
    void roleAndMembershipStatusChangeThroughDomainMethods() {
        GroupMember member =
                GroupMember.join(StudyGroup.create(10L, "알고리즘 스터디"), 20L, GroupRole.MEMBER);

        member.changeRole(GroupRole.MANAGER);
        member.withdraw();

        assertThat(member.getRole()).isEqualTo(GroupRole.MANAGER);
        assertThat(member.getStatus()).isEqualTo(GroupMemberStatus.WITHDRAWN);
    }

    @Test
    void joinRejectsNullArguments() {
        StudyGroup group = StudyGroup.create(10L, "알고리즘 스터디");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> GroupMember.join(null, 20L, GroupRole.MEMBER));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GroupMember.join(group, null, GroupRole.MEMBER));
        assertThatIllegalArgumentException().isThrownBy(() -> GroupMember.join(group, 20L, null));
    }

    @Test
    void changeRoleRejectsNullRole() {
        GroupMember member =
                GroupMember.join(StudyGroup.create(10L, "알고리즘 스터디"), 20L, GroupRole.MEMBER);

        assertThatIllegalArgumentException().isThrownBy(() -> member.changeRole(null));
    }
}
