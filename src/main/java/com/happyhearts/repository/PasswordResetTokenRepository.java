package com.happyhearts.repository;

import com.happyhearts.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("""
            SELECT t FROM PasswordResetToken t
            JOIN FETCH t.user u
            LEFT JOIN FETCH u.branch
            WHERE t.token = :token AND t.usedAt IS NULL AND t.expiresAt > :now
            """)
    Optional<PasswordResetToken> findValidByToken(@Param("token") String token, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.user.id = :userId and t.usedAt is null")
    int deleteAllUnusedForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    int deleteAllByUser_Id(@Param("userId") UUID userId);
}
