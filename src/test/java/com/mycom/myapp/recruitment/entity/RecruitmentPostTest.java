package com.mycom.myapp.recruitment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.member.entity.Member;
import org.junit.jupiter.api.Test;

class RecruitmentPostTest {

    @Test
    void builderInitializesFields() {
        Member leader = Member.create("leader@test.com", "encoded", "리더", null, null, null);

        RecruitmentPost post =
                RecruitmentPost.builder()
                        .leader(leader)
                        .title("제목")
                        .category("개발")
                        .capacity(5)
                        .status(RecruitmentStatus.RECRUITING)
                        .build();

        assertThat(post.getLeader()).isEqualTo(leader);
        assertThat(post.getTitle()).isEqualTo("제목");
        assertThat(post.getCategory()).isEqualTo("개발");
        assertThat(post.getCapacity()).isEqualTo(5);
        assertThat(post.getStatus()).isEqualTo(RecruitmentStatus.RECRUITING);
    }
}
