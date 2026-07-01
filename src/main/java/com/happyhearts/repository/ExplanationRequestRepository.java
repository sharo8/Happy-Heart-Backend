package com.happyhearts.repository;

import com.happyhearts.model.ExplanationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExplanationRequestRepository extends JpaRepository<ExplanationRequest, UUID> {

    @Query("""
            SELECT er.employee.id FROM ExplanationRequest er
            WHERE er.sentAt >= :dayStart AND er.sentAt < :dayEnd
            """)
    List<UUID> findEmployeeIdsWithRequestBetween(@Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);

    boolean existsByEmployee_IdAndSentAtBetween(UUID employeeId, Instant dayStart, Instant dayEnd);
}
