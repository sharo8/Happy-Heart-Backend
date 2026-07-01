package com.happyhearts.repository;

import com.happyhearts.enums.Role;
import com.happyhearts.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "branch")
    @Query("SELECT u FROM User u")
    List<User> findAllWithBranch(Sort sort);

    @EntityGraph(attributePaths = "branch")
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "branch")
    Optional<User> findByInitialSetupToken(String token);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "branch")
    List<User> findByBranch_IdAndRole(UUID branchId, Role role);

    @EntityGraph(attributePaths = "branch")
    List<User> findByRoleIn(Collection<Role> roles, Sort sort);

    @EntityGraph(attributePaths = "branch")
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findWithBranchById(@Param("id") UUID id);

    long countByRole(Role role);

    @EntityGraph(attributePaths = "branch")
    @Query("SELECT u FROM User u WHERE u.active = true")
    List<User> findAllActiveWithBranch();

    @EntityGraph(attributePaths = "branch")
    @Query("SELECT u FROM User u WHERE u.active = true AND u.role IN :roles")
    List<User> findAllActiveWithBranchByRoleIn(@Param("roles") Collection<Role> roles);

    @EntityGraph(attributePaths = "branch")
    @Query("SELECT u FROM User u WHERE u.active = true AND u.branch IS NOT NULL AND u.branch.id IN :branchIds")
    List<User> findAllActiveWithBranchByBranchIdIn(@Param("branchIds") Collection<UUID> branchIds);

    @EntityGraph(attributePaths = "branch")
    @Query("SELECT u FROM User u WHERE u.active = true AND u.branch IS NOT NULL AND u.branch.id = :branchId")
    List<User> findAllActiveWithBranchByBranchId(@Param("branchId") UUID branchId);
}
