package com.happyhearts.repository;

import com.happyhearts.model.GmPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GmPermissionRepository extends JpaRepository<GmPermission, UUID> {

    List<GmPermission> findByUserIdOrderByPageKeyAsc(UUID userId);

    Optional<GmPermission> findByUserIdAndPageKey(UUID userId, String pageKey);

    void deleteByUserId(UUID userId);
}
