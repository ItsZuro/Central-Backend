package com.centrral.centralres.features.orders.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.centrral.centralres.features.orders.model.CashShift;

import jakarta.persistence.LockModeType;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CashShift s WHERE s.cashierUserId = :cashierId AND s.closedAt IS NULL")
    Optional<CashShift> lockCurrent(@Param("cashierId") Long cashierId);

    Optional<CashShift> findFirstByCashierUserIdAndClosedAtIsNull(Long cashierUserId);

    List<CashShift> findTop20ByCashierUserIdAndClosedAtIsNotNullOrderByClosedAtDesc(Long cashierUserId);
}
