package com.happyhearts.repository;

import com.happyhearts.enums.AccessRequestStatus;
import com.happyhearts.model.AccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

    List<AccessRequest> findAllByOrderByCreatedAtDesc();

    List<AccessRequest> findByStatusOrderByCreatedAtDesc(AccessRequestStatus status);

    boolean existsByEmailIgnoreCaseAndStatus(String email, AccessRequestStatus status);

    Optional<AccessRequest> findByIdAndStatus(UUID id, AccessRequestStatus status);
}
