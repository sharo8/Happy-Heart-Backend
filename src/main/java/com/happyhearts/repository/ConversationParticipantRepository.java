package com.happyhearts.repository;

import com.happyhearts.model.ConversationParticipant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

    boolean existsByConversation_IdAndUser_Id(UUID conversationId, UUID userId);

    @EntityGraph(attributePaths = {"user", "user.branch"})
    List<ConversationParticipant> findByConversation_IdOrderByJoinedAtAsc(UUID conversationId);

    @EntityGraph(attributePaths = {"user", "user.branch"})
    List<ConversationParticipant> findByConversation_IdIn(Collection<UUID> conversationIds);
}
