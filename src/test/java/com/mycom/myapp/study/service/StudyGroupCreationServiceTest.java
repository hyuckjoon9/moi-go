package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.study.entity.StudyGroup;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class StudyGroupCreationServiceTest {

    private final StudyGroupRepository studyGroupRepository = mock(StudyGroupRepository.class);
    private final StudyGroupCreationWriter writer = mock(StudyGroupCreationWriter.class);
    private final StudyGroupCreationService service =
            new StudyGroupCreationService(studyGroupRepository, writer);

    private final CreateStudyGroupCommand command =
            new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of(20L));

    @Test
    void returnsExistingGroupIdWithoutWritingAgain() {
        StudyGroup existingGroup = mock(StudyGroup.class);
        when(existingGroup.getId()).thenReturn(100L);
        when(studyGroupRepository.findByPostId(10L)).thenReturn(Optional.of(existingGroup));

        Long groupId = service.create(command);

        assertThat(groupId).isEqualTo(100L);
        verify(writer, never()).create(command);
    }

    @Test
    void returnsNewGroupIdFromWriter() {
        when(studyGroupRepository.findByPostId(10L)).thenReturn(Optional.empty());
        when(writer.create(command)).thenReturn(100L);

        Long groupId = service.create(command);

        assertThat(groupId).isEqualTo(100L);
        verify(writer).create(command);
    }

    @Test
    void returnsExistingGroupIdAfterConcurrentUniqueConflict() {
        StudyGroup existingGroup = mock(StudyGroup.class);
        when(existingGroup.getId()).thenReturn(100L);
        when(studyGroupRepository.findByPostId(10L))
                .thenReturn(Optional.empty(), Optional.of(existingGroup));
        when(writer.create(command))
                .thenThrow(new DataIntegrityViolationException("duplicate post"));

        Long groupId = service.create(command);

        assertThat(groupId).isEqualTo(100L);
    }

    @Test
    void propagatesIntegrityFailureWhenNoConcurrentGroupExists() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("foreign key violation");
        when(studyGroupRepository.findByPostId(10L)).thenReturn(Optional.empty());
        when(writer.create(command)).thenThrow(failure);

        assertThatThrownBy(() -> service.create(command)).isSameAs(failure);
    }
}
