package com.happyhearts.repository;

import com.happyhearts.model.Branch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByCode(String code);

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"leadTeacher.user", "secondTeacher.user"})
    @Query("select b from Branch b where b.id = :id")
    Optional<Branch> findWithLeadersById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"leadTeacher", "secondTeacher"})
    @Query("select b from Branch b")
    List<Branch> findAllWithLeaders();
}
