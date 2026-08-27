package com.lecture.payment.repository;

import com.lecture.payment.entity.CredentialAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CredentialAuditLogRepository
        extends JpaRepository<CredentialAuditLog, Long> {

    List<CredentialAuditLog> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<CredentialAuditLog> findByProjectIdAndActionOrderByCreatedAtDesc(
            Long projectId,
            CredentialAuditLog.Action action
    );
}
