package com.happyhearts.repository;

import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByRfidCardUid(String rfidCardUid);

    Optional<Employee> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    @Query("""
            SELECT COUNT(e) > 0 FROM Employee e
            WHERE e.email IS NOT NULL AND LOWER(TRIM(e.email)) = LOWER(TRIM(:email))
              AND (:excludeId IS NULL OR e.id <> :excludeId)
            """)
    boolean existsByNormalizedEmail(@Param("email") String email, @Param("excludeId") UUID excludeId);

    @EntityGraph(attributePaths = "user")
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findWithUserById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"branch", "user", "user.branch"})
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findWithBranchAndUserById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "branch")
    @Query("select e from Employee e where e.rfidCardUid = :uid")
    Optional<Employee> findWithBranchByRfidCardUid(@Param("uid") String uid);

    @EntityGraph(attributePaths = "branch")
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findWithBranchById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "branch")
    Page<Employee> findByBranch_Id(UUID branchId, Pageable pageable);

    @EntityGraph(attributePaths = "branch")
    Page<Employee> findByBranch_IdAndCategory(UUID branchId, EmployeeCategory category, Pageable pageable);

    @EntityGraph(attributePaths = "branch")
    Page<Employee> findByBranch_IdAndCategoryIn(UUID branchId, Collection<EmployeeCategory> categories, Pageable pageable);

    @EntityGraph(attributePaths = "branch")
    Page<Employee> findByCategory(EmployeeCategory category, Pageable pageable);

    @EntityGraph(attributePaths = "branch")
    @Query("""
            select e from Employee e
            where exists (
                select 1 from Branch b
                where b.leadTeacher.id = e.id
            )
            """)
    Page<Employee> findBranchManagers(Pageable pageable);

    @EntityGraph(attributePaths = "branch")
    @Query("""
            select e from Employee e
            where e.branch.id = :branchId
              and exists (
                  select 1 from Branch b
                  where b.id = :branchId
                    and b.leadTeacher.id = e.id
              )
            """)
    Page<Employee> findBranchManagersByBranch_Id(@Param("branchId") UUID branchId, Pageable pageable);

    @EntityGraph(attributePaths = "branch")
    @Query("select e from Employee e")
    Page<Employee> findAllWithBranch(Pageable pageable);

    @EntityGraph(attributePaths = {"branch", "user"})
    List<Employee> findAllByBranch_Id(UUID branchId);

    List<Employee> findByBranch(Branch branch);

    List<Employee> findAllByBranch_IdAndEmploymentActiveTrue(UUID branchId);

    long countByBranch_Id(UUID branchId);

    List<Employee> findAllByUser_Id(UUID userId);

    @EntityGraph(attributePaths = "branch")
    @Query("SELECT e FROM Employee e WHERE e.user.id = :userId")
    List<Employee> findAllWithBranchByUser_Id(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"branch", "user"})
    @Query("select e from Employee e where e.employmentActive = true")
    List<Employee> findAllActiveWithBranch();

    @EntityGraph(attributePaths = {"branch", "user", "user.branch"})
    @Query("select e from Employee e where e.employmentActive = true and e.branch.id = :branchId")
    List<Employee> findAllActiveByBranch_Id(@Param("branchId") UUID branchId);

    @EntityGraph(attributePaths = {"user", "user.branch"})
    @Query("SELECT e FROM Employee e WHERE e.user IS NOT NULL AND e.employmentActive = true AND e.category IN :cats")
    List<Employee> findActiveWithUserByCategoryIn(@Param("cats") Collection<EmployeeCategory> cats);

    @EntityGraph(attributePaths = {"branch", "user", "user.branch"})
    @Query("SELECT e FROM Employee e WHERE e.employmentActive = true AND e.user IS NOT NULL")
    List<Employee> findAllActiveWithPortalUser();
}
