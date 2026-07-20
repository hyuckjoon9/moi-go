package com.mycom.myapp.study.repository;

import com.mycom.myapp.study.entity.StudyGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {

    Optional<StudyGroup> findByPostId(Long postId);

    boolean existsByPostId(Long postId);
}
