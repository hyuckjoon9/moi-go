package com.mycom.myapp.member.repository;

import com.mycom.myapp.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {}
