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
 * MySQL + Hibernate may leave {@code users.role} as an ENUM with legacy values
 * ({@code BRANCH_MANAGER}, {@code EMPLOYEE}), causing "Data truncated for column 'role'"
 * when inserting expanded portal roles. Normalizes to VARCHAR and migrates legacy rows.
 * Safe to run repeatedly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsersRoleColumnFix {

    private final JdbcTemplate jdbcTemplate;

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void normalizeUsersRoleColumn() {
        if (!isMysql()) {
            return;
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE users MODIFY COLUMN role VARCHAR(50) NOT NULL");
            log.info("Ensured users.role is VARCHAR(50)");
        } catch (Exception e) {
            log.warn("users.role column alter skipped: {}", e.getMessage());
            return;
        }
        try {
            int branchManagers = jdbcTemplate.update(
                    "UPDATE users SET role = 'CENTRAL_COORDINATOR' WHERE role = 'BRANCH_MANAGER'");
            int employees = jdbcTemplate.update(
                    "UPDATE users SET role = 'TEACHER' WHERE role = 'EMPLOYEE'");
            if (branchManagers > 0 || employees > 0) {
                log.info(
                        "Migrated legacy user roles: {} BRANCH_MANAGER -> CENTRAL_COORDINATOR, {} EMPLOYEE -> TEACHER",
                        branchManagers,
                        employees);
            }
        } catch (Exception e) {
            log.warn("Legacy users.role data migration skipped: {}", e.getMessage());
        }
    }

    private boolean isMysql() {
        try (Connection c = jdbcTemplate.getDataSource().getConnection()) {
            String product = c.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("mysql");
        } catch (Exception e) {
            log.debug("Could not read database product: {}", e.getMessage());
            return false;
        }
    }
}
