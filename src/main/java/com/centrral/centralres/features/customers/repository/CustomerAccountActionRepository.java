package com.centrral.centralres.features.customers.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.centrral.centralres.features.customers.model.CustomerAccountAction;

public interface CustomerAccountActionRepository extends JpaRepository<CustomerAccountAction, Long> {
    List<CustomerAccountAction> findTop20ByCustomerIdOrderByOccurredAtDesc(Long customerId);
}
