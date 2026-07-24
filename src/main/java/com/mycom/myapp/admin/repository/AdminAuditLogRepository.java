package com.mycom.myapp.admin.repository;

import com.mycom.myapp.admin.entity.AdminAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findTop10ByOrderByCreatedAtDescIdDesc();
}
