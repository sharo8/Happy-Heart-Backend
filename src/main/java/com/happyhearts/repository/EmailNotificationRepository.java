package com.happyhearts.repository;

import com.happyhearts.enums.NotificationStatus;
import com.happyhearts.enums.NotificationType;
import com.happyhearts.model.EmailNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, UUID> {

    Page<EmailNotification> findByStatus(NotificationStatus status, Pageable pageable);

    Page<EmailNotification> findByNotificationType(NotificationType type, Pageable pageable);

    Page<EmailNotification> findByStatusAndNotificationType(
            NotificationStatus status, NotificationType type, Pageable pageable);

    Page<EmailNotification> findAll(Pageable pageable);
}
