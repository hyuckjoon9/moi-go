package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.study.entity.GroupMember;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StudyGroupCreationWriterTest {

    private final StudyGroupRepository studyGroupRepository = mock(StudyGroupRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final StudyGroupCreationWriter writer =
            new StudyGroupCreationWriter(studyGroupRepository, groupMemberRepository);

    @Test
    void savesLeaderAndDistinctApprovedMembersInInputOrder() {
        StudyGroup savedGroup = mock(StudyGroup.class);
        when(savedGroup.getId()).thenReturn(100L);
        when(studyGroupRepository.saveAndFlush(any(StudyGroup.class))).thenReturn(savedGroup);
        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of(20L, 1L, 20L, 30L));

        Long groupId = writer.create(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GroupMember>> membersCaptor = ArgumentCaptor.forClass(List.class);
        verify(groupMemberRepository).saveAll(membersCaptor.capture());
        verify(groupMemberRepository).flush();
        assertThat(groupId).isEqualTo(100L);
        assertThat(membersCaptor.getValue())
                .extracting(GroupMember::getUserId, GroupMember::getRole)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, GroupRole.LEADER),
                        org.assertj.core.groups.Tuple.tuple(20L, GroupRole.MEMBER),
                        org.assertj.core.groups.Tuple.tuple(30L, GroupRole.MEMBER));
    }

    @Test
    void savesOnlyLeaderWhenApprovedMemberListIsEmpty() {
        StudyGroup savedGroup = mock(StudyGroup.class);
        when(savedGroup.getId()).thenReturn(100L);
        when(studyGroupRepository.saveAndFlush(any(StudyGroup.class))).thenReturn(savedGroup);
        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of());

        writer.create(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GroupMember>> membersCaptor = ArgumentCaptor.forClass(List.class);
        verify(groupMemberRepository).saveAll(membersCaptor.capture());
        assertThat(membersCaptor.getValue())
                .singleElement()
                .satisfies(
                        member -> {
                            assertThat(member.getUserId()).isEqualTo(1L);
                            assertThat(member.getRole()).isEqualTo(GroupRole.LEADER);
                        });
    }
}
