package com.happyhearts.repository;

import com.happyhearts.model.AuditLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @EntityGraph(attributePaths = {"user", "branch"})
    @Override
    Optional<AuditLog> findById(UUID id);
}
