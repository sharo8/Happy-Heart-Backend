package com.happyhearts.repository;

import com.happyhearts.model.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

    List<InAppNotification> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT n FROM InAppNotification n WHERE n.user.id = :userId AND n.id = :id")
    Optional<InAppNotification> findByIdAndUser_Id(@Param("id") UUID id, @Param("userId") UUID userId);

    long countByUser_IdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.readAt = :readAt WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllReadForUser(@Param("userId") UUID userId, @Param("readAt") Instant readAt);

    @Modifying
    @Query("delete from InAppNotification n where n.user.id = :userId")
    int deleteAllByUser_Id(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from InAppNotification n where n.id = :id and n.user.id = :userId and n.readAt is not null")
    int deleteReadByIdAndUser_Id(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("delete from InAppNotification n where n.user.id = :userId and n.readAt is not null")
    int deleteAllReadByUser_Id(@Param("userId") UUID userId);
}
