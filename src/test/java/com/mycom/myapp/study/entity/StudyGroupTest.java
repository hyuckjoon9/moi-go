package com.mycom.myapp.study.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class StudyGroupTest {

    @Test
    void createInitializesActiveGroup() {
        StudyGroup group = StudyGroup.create(10L, "알고리즘 스터디");

        assertThat(group.getPostId()).isEqualTo(10L);
        assertThat(group.getName()).isEqualTo("알고리즘 스터디");
        assertThat(group.getStatus()).isEqualTo(GroupStatus.ACTIVE);
    }

    @Test
    void endChangesStatus() {
        StudyGroup group = StudyGroup.create(10L, "알고리즘 스터디");

        group.end();

        assertThat(group.getStatus()).isEqualTo(GroupStatus.ENDED);
    }

    @Test
    void createRejectsNullPostId() {
        assertThatIllegalArgumentException().isThrownBy(() -> StudyGroup.create(null, "알고리즘 스터디"));
    }

    @Test
    void createRejectsBlankName() {
        assertThatIllegalArgumentException().isThrownBy(() -> StudyGroup.create(10L, " "));
    }
}
