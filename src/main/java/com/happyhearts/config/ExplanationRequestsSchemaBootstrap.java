package com.happyhearts.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures {@code explanation_requests} exists when Flyway is disabled (dev MySQL + ddl-auto).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExplanationRequestsSchemaBootstrap {

    private final JdbcTemplate jdbcTemplate;

    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    public void ensureExplanationRequestsTable() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS explanation_requests (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        employee_id CHAR(36) NOT NULL,
                        admin_id CHAR(36) NULL,
                        reason TEXT NOT NULL,
                        subject VARCHAR(500) NULL,
                        sent_at TIMESTAMP(6) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        CONSTRAINT fk_explanation_employee
                            FOREIGN KEY (employee_id) REFERENCES employees(id),
                        CONSTRAINT fk_explanation_admin
                            FOREIGN KEY (admin_id) REFERENCES users(id)
                    )
                    """);
            try {
                jdbcTemplate.execute(
                        "CREATE INDEX idx_explanation_employee_sent ON explanation_requests (employee_id, sent_at)");
            } catch (Exception indexEx) {
                log.debug("explanation_requests index already exists or skipped: {}", indexEx.getMessage());
            }
            log.info("Ensured explanation_requests table exists");
        } catch (Exception e) {
            log.warn("Could not bootstrap explanation_requests table: {}", e.getMessage());
        }
    }
}
