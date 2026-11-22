package com.centrral.centralres.features.orders.init;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Idempotent PostgreSQL migration for existing production databases.
 * Hibernate ddl-auto=update does not reliably remove preexisting NOT NULL constraints.
 * A guest sale uses orders.customer_id = NULL, without a fake Customer/User account.
 */
@Component
@Profile("prod")
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class WalkInOrderSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        Integer constrained = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'orders'
                  AND column_name = 'customer_id'
                  AND is_nullable = 'NO'
                """, Integer.class);

        if (constrained != null && constrained > 0) {
            jdbc.execute("ALTER TABLE orders ALTER COLUMN customer_id DROP NOT NULL");
            log.info("Esquema actualizado: orders.customer_id admite compras presenciales sin registro.");
        }
    }
}
