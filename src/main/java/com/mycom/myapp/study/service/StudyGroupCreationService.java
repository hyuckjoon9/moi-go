package com.mycom.myapp.study.service;

import com.mycom.myapp.study.repository.StudyGroupRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class StudyGroupCreationService {

    private final StudyGroupRepository studyGroupRepository;
    private final StudyGroupCreationWriter writer;

    StudyGroupCreationService(
            StudyGroupRepository studyGroupRepository, StudyGroupCreationWriter writer) {
        this.studyGroupRepository = studyGroupRepository;
        this.writer = writer;
    }

    public Long create(CreateStudyGroupCommand command) {
        return studyGroupRepository
                .findByPostId(command.postId())
                .map(group -> group.getId())
                .orElseGet(() -> createNewOrReturnConcurrentGroup(command));
    }

    private Long createNewOrReturnConcurrentGroup(CreateStudyGroupCommand command) {
        try {
            return writer.create(command);
        } catch (DataIntegrityViolationException failure) {
            return studyGroupRepository
                    .findByPostId(command.postId())
                    .map(group -> group.getId())
                    .orElseThrow(() -> failure);
        }
    }
}
