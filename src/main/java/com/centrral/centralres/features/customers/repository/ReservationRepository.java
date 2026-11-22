package com.centrral.centralres.features.customers.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.centrral.centralres.features.customers.enums.ReservationStatus;
import com.centrral.centralres.features.customers.model.Reservation;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

        Page<Reservation> findByCustomerId(Long customerId, Pageable pageable);

        // El calendario se interpreta en la zona horaria de la sucursal (America/Lima).
        // No cuentan reservas canceladas, completadas o que ya hayan pasado.
        @Query("""
                    SELECT r FROM Reservation r
                    WHERE r.customer.id = :customerId
                      AND r.status IN :statuses
                      AND (r.reservationDate > :today
                        OR (r.reservationDate = :today AND r.reservationTime >= :nowTime))
                """)
        List<Reservation> findActiveUpcomingForCustomer(
                        @Param("customerId") Long customerId,
                        @Param("statuses") Collection<ReservationStatus> statuses,
                        @Param("today") LocalDate today,
                        @Param("nowTime") LocalTime nowTime);

        List<Reservation> findByTable_IdAndReservationDate(Long tableId, LocalDate date);

        List<Reservation> findByStatusAndReservationDateOrderByReservationTimeAsc(
                        ReservationStatus status,
                        LocalDate reservationDate);

        @Query("""
                            SELECT r FROM Reservation r
                            WHERE r.table.id = :tableId
                              AND r.status IN ('CONFIRMED', 'PENDING')
                              AND r.startDateTime < :endTime
                              AND r.endDateTime > :startTime
                        """)
        List<Reservation> findOverlappingReservations(
                        @Param("tableId") Long tableId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

        @Query("SELECT COUNT(r) FROM Reservation r WHERE r.reservationDate = :date")
        int countReservationsByDate(@Param("date") LocalDate date);

        @Query("SELECT r FROM Reservation r WHERE r.reservationDate >= CURRENT_DATE ORDER BY r.reservationDate ASC")
        List<Reservation> findUpcomingReservations();

        List<Reservation> findByReservationDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

        long countByReservationDateBetween(LocalDate startDate, LocalDate endDate);

        boolean existsByIdAndCustomer_Id(Long id, Long customerId);
}
