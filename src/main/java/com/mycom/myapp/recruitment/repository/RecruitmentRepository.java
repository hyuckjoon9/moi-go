package com.mycom.myapp.recruitment.repository;

import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitmentRepository extends JpaRepository<RecruitmentPost, Long> {}
