package com.centrral.centralres.features.orders.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.centrral.centralres.features.customers.model.Customer;
import com.centrral.centralres.features.orders.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // Evita carreras entre cobro Niubiz y cancelación de un mismo pedido.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT o FROM CustomerOrder o
            WHERE o.status.code IN :activeCodes
               OR (o.status.code IN :recentCodes AND o.date >= :todayStart)
            ORDER BY o.date ASC, o.id ASC
            """)
    List<Order> findForKitchenBoard(
            @Param("activeCodes") List<String> activeCodes,
            @Param("recentCodes") List<String> recentCodes,
            @Param("todayStart") LocalDateTime todayStart);

    List<Order> findByCustomer(Customer customer);

    Page<Order> findByCustomer_Id(Long customerId, Pageable pageable);

    List<Order> findByEmployee_IdAndType_Code(Long employeeId, String typeCode);

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE o.date BETWEEN :startOfDay AND :endOfDay")
    int countOrdersByDate(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("""
            SELECT COALESCE(SUM(o.total), 0)
            FROM CustomerOrder o
            WHERE o.date BETWEEN :startOfDay AND :endOfDay
            """)
    BigDecimal calculateSalesByDate(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("""
                SELECT COALESCE(SUM(o.total), 0)
                FROM CustomerOrder o
                WHERE o.status.code IN :validStatuses
            """)
    BigDecimal sumTotalByStatusCodes(@Param("validStatuses") List<String> validStatuses);

    @Query("""
                SELECT tr.name, COUNT(o), COALESCE(SUM(o.total), 0)
                FROM CustomerOrder o
                JOIN o.type ot
                JOIN ot.translations tr
                WHERE tr.lang = :lang
                GROUP BY tr.name
            """)
    List<Object[]> findOrderTypeStatistics(@Param("lang") String lang);

    boolean existsByIdAndCustomer_Id(Long id, Long customerId);

    List<Order> findByType_Code(String typeCode);

    List<Order> findByType_CodeIn(List<String> typeCodes);

    @Query("SELECT o FROM CustomerOrder o JOIN o.status s JOIN o.type t WHERE s.code IN :statuses AND t.code = :typeCode")
    List<Order> findActiveOrdersByType(
            @Param("statuses") List<String> statuses,
            @Param("typeCode") String typeCode);

}
