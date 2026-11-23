package com.centrral.centralres.core.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Serializes scheduled tasks across ECS replicas using session-level
 * PostgreSQL advisory locks. The lock is held on the same dedicated connection
 * until the job completes, and is released if the task or connection fails.
 */
@Component
@Slf4j
public class DistributedSchedulerLock {
    private final DataSource dataSource;
    private final boolean enabled;

    public DistributedSchedulerLock(DataSource dataSource,
            @Value("${app.scheduler.distributed.enabled:false}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    public void runOnce(long key, Runnable job) {
        if (!enabled) {
            job.run();
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            boolean acquired;
            try (PreparedStatement acquire =
                    connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                acquire.setLong(1, key);
                try (ResultSet rs = acquire.executeQuery()) {
                    acquired = rs.next() && rs.getBoolean(1);
                }
            }
            if (!acquired) {
                log.debug("Skipping scheduled task {}: another ECS task holds the lock", key);
                return;
            }
            try {
                job.run();
            } finally {
                try (PreparedStatement release =
                        connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    release.setLong(1, key);
                    release.execute();
                }
            }
        } catch (SQLException ex) {
            log.warn("Scheduled task {} skipped due to database locking error: {}",
                    key, ex.toString());
        }
    }
}
