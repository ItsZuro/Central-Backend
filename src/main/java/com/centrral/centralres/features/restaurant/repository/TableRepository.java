package com.centrral.centralres.features.restaurant.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.centrral.centralres.features.restaurant.model.TableEntity;

@Repository
public interface TableRepository extends JpaRepository<TableEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TableEntity t WHERE t.id = :id")
    Optional<TableEntity> findByIdForUpdate(@Param("id") Long id);
}
