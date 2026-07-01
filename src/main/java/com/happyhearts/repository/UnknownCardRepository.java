package com.happyhearts.repository;

import com.happyhearts.model.UnknownCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnknownCardRepository extends JpaRepository<UnknownCard, Long> {

    Optional<UnknownCard> findByUid(String uid);

    List<UnknownCard> findAllByOrderByLastSeenDesc();
}
