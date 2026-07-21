package com.mycom.myapp.application.repository;

import com.mycom.myapp.application.entity.JoinApplication;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinApplicationRepository extends JpaRepository<JoinApplication, Long> {

    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);

    Optional<JoinApplication> findByIdAndPostId(Long id, Long postId);

    List<JoinApplication> findByPostId(Long postId);
}
