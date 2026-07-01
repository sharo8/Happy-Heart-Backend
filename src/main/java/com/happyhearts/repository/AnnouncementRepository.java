package com.happyhearts.repository;

import com.happyhearts.enums.AnnouncementStatus;
import com.happyhearts.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID>, JpaSpecificationExecutor<Announcement> {

    List<Announcement> findByStatusAndScheduledAtLessThanEqual(AnnouncementStatus status, Instant now);
}
