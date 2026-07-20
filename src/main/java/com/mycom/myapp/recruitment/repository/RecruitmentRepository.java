package com.mycom.myapp.recruitment.repository;

import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitmentRepository extends JpaRepository<RecruitmentPost, Long> {

    Page<RecruitmentPost> findByCategory(String category, Pageable pageable);
}
