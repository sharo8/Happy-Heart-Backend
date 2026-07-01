package com.happyhearts.repository;

import com.happyhearts.model.InternalEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InternalEmailRepository extends JpaRepository<InternalEmail, UUID> {

    @EntityGraph(attributePaths = {"fromUser", "fromUser.branch", "toUser", "toUser.branch"})
    @Query(
            "SELECT e FROM InternalEmail e WHERE "
                    + "((:folder = 'sent' AND e.fromUser.id = :userId AND e.folder = 'sent') "
                    + "OR (:folder = 'drafts' AND e.fromUser.id = :userId AND e.folder = 'drafts') "
                    + "OR (:folder NOT IN ('sent', 'drafts') AND e.toUser.id = :userId AND e.folder = :folder)) "
                    + "AND (:q = '' OR LOWER(e.subject) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(e.body) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(e.fromUser.email) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(e.fromUser.firstName) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(e.fromUser.lastName) LIKE LOWER(CONCAT('%', :q, '%'))) "
                    + "ORDER BY e.sentAt DESC, e.createdAt DESC"
    )
    Page<InternalEmail> searchForUser(
            @Param("userId") UUID userId,
            @Param("folder") String folder,
            @Param("q") String q,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"fromUser", "fromUser.branch", "toUser", "toUser.branch"})
    List<InternalEmail> findByThreadIdOrderBySentAtAsc(UUID threadId);

    long countByToUser_IdAndFolderAndReadFalse(UUID userId, String folder);

    @EntityGraph(attributePaths = {"fromUser", "toUser"})
  Optional<InternalEmail> findWithUsersById(UUID id);
}
