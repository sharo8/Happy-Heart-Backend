package com.happyhearts.repository;

import com.happyhearts.model.OfflineSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineSyncLogRepository extends JpaRepository<OfflineSyncLog, Long> {
}
