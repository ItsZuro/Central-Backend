package com.centrral.centralres.features.orders.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cash_register_entries", uniqueConstraints = {
    @UniqueConstraint(name = "uq_cash_entry_payment", columnNames = {"payment_id"}),
    @UniqueConstraint(name = "uq_cash_entry_method_reference", columnNames = {"method_code", "payment_reference"})
})
@Getter
@Setter
public class CashEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_shift_id", nullable = false)
    private CashShift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "entry_type", nullable = false, length = 20)
    private String type;

    @Column(name = "method_code", nullable = false, length = 30)
    private String methodCode;

    @Column(name = "payment_reference", length = 100)
    private String reference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
