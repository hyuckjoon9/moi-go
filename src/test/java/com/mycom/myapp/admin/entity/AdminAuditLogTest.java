package com.mycom.myapp.admin.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AdminAuditLogTest {

    @Test
    void createNormalizesReasonAndKeepsSnapshots() {
        AdminAuditLog log =
                AdminAuditLog.create(
                        12L,
                        AdminAction.RECRUITMENT_HIDDEN,
                        AdminTargetType.RECRUITMENT,
                        37L,
                        "스프링 스터디원 모집",
                        "{\"visibility\":\"VISIBLE\"}",
                        "{\"visibility\":\"HIDDEN\"}",
                        "  운영 정책 위반 콘텐츠 확인  ");

        assertThat(log.getAdminId()).isEqualTo(12L);
        assertThat(log.getTargetLabel()).isEqualTo("스프링 스터디원 모집");
        assertThat(log.getReason()).isEqualTo("운영 정책 위반 콘텐츠 확인");
        assertThat(log.getBeforeSnapshot()).contains("VISIBLE");
        assertThat(log.getAfterSnapshot()).contains("HIDDEN");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "짧음"})
    void createRejectsInvalidReason(String reason) {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                AdminAuditLog.create(
                                        12L,
                                        AdminAction.MEMBER_STATUS_CHANGED,
                                        AdminTargetType.MEMBER,
                                        1L,
                                        "정지 대상 회원",
                                        "{}",
                                        "{}",
                                        reason));
    }
}
