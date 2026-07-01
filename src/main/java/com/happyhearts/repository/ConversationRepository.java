package com.happyhearts.repository;

import com.happyhearts.model.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @EntityGraph(attributePaths = "branch")
    @Query(
            "SELECT p.conversation FROM ConversationParticipant p "
                    + "WHERE p.user.id = :userId "
                    + "ORDER BY p.conversation.createdAt DESC"
    )
    Page<Conversation> findForParticipant(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "branch")
    @Override
    Optional<Conversation> findById(UUID id);
}
