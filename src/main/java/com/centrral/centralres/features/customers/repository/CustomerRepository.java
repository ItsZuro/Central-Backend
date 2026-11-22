package com.centrral.centralres.features.customers.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.centrral.centralres.features.customers.model.Customer;

import jakarta.persistence.LockModeType;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByUserId(Long userId);

    long countByUser_EnabledTrue();

    long countByUser_EnabledFalse();

    @Query("""
            SELECT c FROM Customer c JOIN c.user u
            WHERE (:enabled IS NULL OR u.enabled = :enabled)
              AND (:term IS NULL
                  OR LOWER(u.username) LIKE CONCAT('%', :term, '%')
                  OR LOWER(u.firstName) LIKE CONCAT('%', :term, '%')
                  OR LOWER(u.lastName) LIKE CONCAT('%', :term, '%')
                  OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE CONCAT('%', :term, '%')
                  OR LOWER(u.email) LIKE CONCAT('%', :term, '%'))
            """)
    Page<Customer> findForManagement(@Param("term") String term,
                                    @Param("enabled") Boolean enabled,
                                    Pageable pageable);


    boolean existsByUser_Id(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdWithLock(@Param("id") Long id);

    @Query(value = "SELECT * FROM customers ORDER BY points DESC LIMIT :limit", nativeQuery = true)
    List<Customer> findTopCustomersByPoints(@Param("limit") int limit);

    @Query("SELECT c FROM Customer c JOIN c.user u " +
            "WHERE LOWER(u.firstName) LIKE %:query% " +
            "OR LOWER(u.lastName) LIKE %:query% " +
            "OR LOWER(u.email) LIKE %:query%")
    List<Customer> searchByQuery(@Param("query") String query);

}
