package com.mycom.myapp.application.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JoinApplicationTest {

    @Test
    void builderInitializesFieldsWithPendingStatus() {
        JoinApplication application =
                JoinApplication.builder().motivation("지원동기").experience("경험").build();

        assertThat(application.getMotivation()).isEqualTo("지원동기");
        assertThat(application.getExperience()).isEqualTo("경험");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(application.getAppliedAt()).isNotNull();
    }

    @Test
    void approveChangesStatusToApproved() {
        JoinApplication application = JoinApplication.builder().motivation("지원동기").build();

        application.approve();

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void rejectChangesStatusToRejected() {
        JoinApplication application = JoinApplication.builder().motivation("지원동기").build();

        application.reject();

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }
}