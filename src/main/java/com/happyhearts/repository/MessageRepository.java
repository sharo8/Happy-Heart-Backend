package com.happyhearts.repository;

import com.happyhearts.model.Message;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @EntityGraph(attributePaths = {"sender", "sender.branch", "replyTo"})
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId AND m.deleted = false ORDER BY m.sentAt ASC")
    List<Message> findByConversation_IdOrderBySentAtAsc(@Param("conversationId") UUID conversationId);

    @EntityGraph(attributePaths = {"sender"})
    @Query("SELECT m FROM Message m WHERE m.conversation.id IN :ids AND m.deleted = false ORDER BY m.sentAt DESC")
    List<Message> findByConversation_IdInOrderBySentAtDesc(@Param("ids") List<UUID> ids);

    @Query(
            "SELECT m.conversation.id, COUNT(m) FROM Message m "
                    + "WHERE m.conversation.id IN :ids AND m.sender.id <> :userId AND m.seen = false AND m.deleted = false "
                    + "GROUP BY m.conversation.id"
    )
    List<Object[]> countUnreadByConversation(@Param("ids") List<UUID> ids, @Param("userId") UUID userId);

    @Query(
            "SELECT COUNT(m) FROM Message m "
                    + "WHERE m.sender.id <> :userId AND m.seen = false AND m.deleted = false "
                    + "AND EXISTS (SELECT 1 FROM ConversationParticipant cp "
                    + "WHERE cp.conversation.id = m.conversation.id AND cp.user.id = :userId)"
    )
    long countTotalUnreadForUser(@Param("userId") UUID userId);

    @Modifying
    @Query(
            "UPDATE Message m SET m.seen = true WHERE m.conversation.id = :conversationId "
                    + "AND m.sender.id <> :userId AND m.seen = false"
    )
    int markConversationReadForUser(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);
}
