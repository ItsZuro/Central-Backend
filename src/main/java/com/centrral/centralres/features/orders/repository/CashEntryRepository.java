package com.centrral.centralres.features.orders.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.centrral.centralres.features.orders.model.CashEntry;

public interface CashEntryRepository extends JpaRepository<CashEntry, Long> {
    List<CashEntry> findByShift_IdOrderByCreatedAtAscIdAsc(Long shiftId);
    boolean existsByPayment_Id(Long paymentId);
}
