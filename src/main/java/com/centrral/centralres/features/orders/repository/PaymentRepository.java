package com.centrral.centralres.features.orders.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.centrral.centralres.features.employees.model.Employee;
import com.centrral.centralres.features.orders.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    boolean existsByOrder_IdAndStatus(Long orderId, com.centrral.centralres.features.orders.enums.PaymentStatus status);
    boolean existsByPaymentMethod_IdAndTransactionCodeAndStatus(
            Long paymentMethodId, String transactionCode,
            com.centrral.centralres.features.orders.enums.PaymentStatus status);


    List<Payment> findAllByOrder_EmployeeAndOrder_DateAfter(Employee employee, LocalDateTime startTime);

    // Cambia 'p.paymentMethod.name' por 'p.paymentMethod.code'
    // (o cual sea el nombre del campo en tu entidad PaymentMethod.java)
    @Query("SELECT p.paymentMethod.code, COUNT(p), SUM(p.amount) " +
            "FROM Payment p " +
            "WHERE p.status = 'CONFIRMED' " +
            "GROUP BY p.paymentMethod.code")
    List<Object[]> findPaymentSummaryByMethod();
}
