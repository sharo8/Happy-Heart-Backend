package com.happyhearts.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;

/**
 * MySQL + Hibernate may map {@code notification_type} as ENUM missing newer values (e.g.
 * {@code PASSWORD_RESET}), causing "Data truncated for column 'notification_type'". This
 * normalizes the column to VARCHAR. Safe to run repeatedly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationColumnFix {

    private final JdbcTemplate jdbcTemplate;

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void normalizeNotificationTypeColumn() {
        try (Connection c = jdbcTemplate.getDataSource().getConnection()) {
            String product = c.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("mysql")) {
                return;
            }
        } catch (Exception e) {
            log.debug("Could not read database product: {}", e.getMessage());
            return;
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE email_notifications MODIFY COLUMN notification_type VARCHAR(40) NOT NULL");
            log.debug("Ensured email_notifications.notification_type is VARCHAR(40)");
        } catch (Exception e) {
            log.debug("email_notifications.notification_type alter skipped: {}", e.getMessage());
        }
    }
}
