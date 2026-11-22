package com.centrral.centralres.features.orders.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.centrral.centralres.features.orders.model.OrderDetail;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    @Query("""
                SELECT d.product.id, d.product.name, SUM(d.quantity), SUM(d.quantity * d.unitPrice)
                FROM OrderDetail d
                GROUP BY d.product.id, d.product.name
                ORDER BY SUM(d.quantity) DESC
            """)
    List<Object[]> findTopSellingProducts();

    boolean existsByProduct_IdAndOrder_Customer_User_Username(Long productId, String username);

    // Reseñas verificadas: el producto debe pertenecer a un pedido finalizado del cliente.
    @Query("""
            SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END
            FROM OrderDetail d
            WHERE d.product.id = :productId
              AND d.order.customer.id = :customerId
              AND d.order.status.code IN ('COMPLETED', 'DELIVERED')
            """)
    boolean existsCompletedPurchase(
            @Param("productId") Long productId,
            @Param("customerId") Long customerId);

}
