package com.centrral.centralres.features.restaurant.init;

import java.time.LocalTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.centrral.centralres.features.restaurant.enums.TableStatus;
import com.centrral.centralres.features.restaurant.model.TableEntity;
import com.centrral.centralres.features.restaurant.repository.TableRepository;
import lombok.RequiredArgsConstructor;

/**
 * Opt-in initial tables for an otherwise empty academic demo database.
 * Never overwrites tables managed by restaurant staff.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.seed-tables", havingValue = "true")
public class DemoTableInitializer {
    private final TableRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {
        if (repository.count() > 0) return;
        repository.saveAll(List.of(
                table("M01", "Interior 1", 2, 1),
                table("M02", "Interior 2", 4, 2),
                table("M03", "Interior 3", 4, 3),
                table("M04", "Interior familiar", 6, 4),
                table("M05", "Mesa grupal", 10, 5)
        ));
    }

    private TableEntity table(String code, String alias, int capacity, int priority) {
        return TableEntity.builder()
                .code(code).alias(alias).capacity(capacity)
                .minCapacity(1).optimalCapacity(capacity).priority(priority)
                .description("Mesa de demostración")
                .openTime(LocalTime.of(12, 0)).closeTime(LocalTime.of(23, 0))
                .reservationDurationMinutes(60)
                .bufferBeforeMinutes(10).bufferAfterMinutes(15)
                .status(TableStatus.FREE)
                .build();
    }
}
