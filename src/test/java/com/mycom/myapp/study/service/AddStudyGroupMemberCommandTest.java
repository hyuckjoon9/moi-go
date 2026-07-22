package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AddStudyGroupMemberCommandTest {

    @Test
    void rejectsNullPostId() {
        assertThatThrownBy(() -> new AddStudyGroupMemberCommand(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullUserId() {
        assertThatThrownBy(() -> new AddStudyGroupMemberCommand(10L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
