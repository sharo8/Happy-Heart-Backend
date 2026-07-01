package com.happyhearts.repository;

import com.happyhearts.enums.FeedbackVisibility;
import com.happyhearts.model.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Feedback> {

    @EntityGraph(attributePaths = {"fromUser", "toUser", "branch"})
    @Query("SELECT f FROM Feedback f ORDER BY f.createdAt DESC")
    Page<Feedback> findAllDetailed(Pageable pageable);

    @EntityGraph(attributePaths = {"fromUser", "toUser", "branch"})
    Page<Feedback> findByToUser_IdOrderByCreatedAtDesc(UUID toUserId, Pageable pageable);

    @EntityGraph(attributePaths = {"fromUser", "toUser", "branch"})
    @Query("""
            SELECT f FROM Feedback f
            WHERE f.toUser.id = :uid OR f.visibility IN :vis
            ORDER BY f.createdAt DESC
            """)
    Page<Feedback> findForGmp(@Param("uid") UUID uid, @Param("vis") Collection<FeedbackVisibility> vis, Pageable pageable);

    @EntityGraph(attributePaths = {"fromUser", "toUser", "branch"})
    @Query("""
            SELECT f FROM Feedback f
            WHERE (
                (f.branch IS NOT NULL AND f.branch.id = :bid
                    AND (f.visibility IN ('SUPERIORS','PUBLIC') OR f.toUser.id = :uid OR f.fromUser.id = :uid))
                OR (f.branch IS NULL AND (f.toUser.id = :uid OR f.fromUser.id = :uid))
            )
            ORDER BY f.createdAt DESC
            """)
    Page<Feedback> findBranchScopedVisible(@Param("bid") UUID branchId, @Param("uid") UUID uid, Pageable pageable);

    @EntityGraph(attributePaths = {"fromUser", "toUser", "branch"})
    @Override
    Optional<Feedback> findById(UUID id);
}
