package com.happyhearts.repository;

import com.happyhearts.model.LoginOtpChallenge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoginOtpChallengeRepository extends JpaRepository<LoginOtpChallenge, UUID> {

    @EntityGraph(attributePaths = "user")
    @Query("select c from LoginOtpChallenge c where c.id = :id")
    Optional<LoginOtpChallenge> findWithUserById(@Param("id") UUID id);

    @Modifying
    @Query("delete from LoginOtpChallenge c where c.user.id = :userId and c.used = false")
    int deleteUnusedByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from LoginOtpChallenge c where c.user.id = :userId")
    int deleteAllByUser_Id(@Param("userId") UUID userId);
}
