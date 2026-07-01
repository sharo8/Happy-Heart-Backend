package com.happyhearts.repository;

import com.happyhearts.model.RfidReader;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RfidReaderRepository extends JpaRepository<RfidReader, UUID> {

    List<RfidReader> findByBranch_Id(UUID branchId);

    boolean existsByReaderCode(String readerCode);

    Optional<RfidReader> findByReaderCode(String readerCode);

    @EntityGraph(attributePaths = "branch")
    @Query("select r from RfidReader r where r.readerCode = :readerCode")
    Optional<RfidReader> findWithBranchByReaderCode(@Param("readerCode") String readerCode);

    @EntityGraph(attributePaths = "branch")
    @Query("select r from RfidReader r where r.id = :id")
    Optional<RfidReader> findWithBranchById(@Param("id") UUID id);
}
