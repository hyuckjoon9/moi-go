package com.mycom.myapp.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.admin.entity.AdminAction;
import com.mycom.myapp.admin.entity.AdminAuditLog;
import com.mycom.myapp.admin.entity.AdminTargetType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class AdminAuditLogRepositoryTest {

    @Autowired private AdminAuditLogRepository repository;

    @Test
    void findsRecentActionsInCreatedAtAndIdDescendingOrder() {
        repository.save(
                AdminAuditLog.create(
                        12L,
                        AdminAction.RECRUITMENT_HIDDEN,
                        AdminTargetType.RECRUITMENT,
                        1L,
                        "첫 번째 모집글",
                        "{\"visibility\":\"VISIBLE\"}",
                        "{\"visibility\":\"HIDDEN\"}",
                        "운영 정책 위반 콘텐츠 확인"));
        repository.save(
                AdminAuditLog.create(
                        12L,
                        AdminAction.RECRUITMENT_RESTORED,
                        AdminTargetType.RECRUITMENT,
                        2L,
                        "두 번째 모집글",
                        "{\"visibility\":\"HIDDEN\"}",
                        "{\"visibility\":\"VISIBLE\"}",
                        "반복적인 운영 정책 위반 확인"));

        List<AdminAuditLog> result = repository.findTop10ByOrderByCreatedAtDescIdDesc();

        assertThat(result).extracting(AdminAuditLog::getTargetId).containsExactly(2L, 1L);
    }
}
