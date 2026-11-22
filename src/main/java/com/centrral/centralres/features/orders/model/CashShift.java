package com.centrral.centralres.features.orders.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cash_register_shifts")
@Getter
@Setter
public class CashShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "cashier_user_id", nullable = false)
    private Long cashierUserId;
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "opening_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingCash;
    @Column(name = "expected_cash", precision = 12, scale = 2)
    private BigDecimal expectedCash;
    @Column(name = "counted_cash", precision = 12, scale = 2)
    private BigDecimal countedCash;
    @Column(name = "cash_difference", precision = 12, scale = 2)
    private BigDecimal cashDifference;
    @Column(name = "closing_note", length = 500)
    private String closingNote;
}
