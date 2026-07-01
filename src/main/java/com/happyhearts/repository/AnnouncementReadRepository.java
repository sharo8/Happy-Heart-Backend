package com.happyhearts.repository;

import com.happyhearts.model.AnnouncementRead;
import com.happyhearts.model.AnnouncementReadKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, AnnouncementReadKey> {

    boolean existsById_AnnouncementIdAndId_UserId(UUID announcementId, UUID userId);

    @Query("SELECT ar.id.announcementId FROM AnnouncementRead ar WHERE ar.id.userId = :userId AND ar.id.announcementId IN :ids")
    Set<UUID> findReadIds(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);
}
