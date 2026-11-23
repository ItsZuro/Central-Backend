package com.centrral.centralres.core.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Distributes short STOMP notifications to all running ECS tasks using the
 * existing PostgreSQL database. LISTEN holds one connection per task. Payloads
 * must contain only non-sensitive invalidation hints; clients fetch protected
 * details through the authenticated REST API.
 *
 * PostgreSQL NOTIFY is not a durable queue. REST remains the source of truth.
 */
@Component
@Slf4j
public class PostgresWebSocketBridge {
    private static final String CHANNEL = "central_ws_events";

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final SimpMessagingTemplate socket;
    private final boolean clustered;

    private volatile boolean running;
    private ExecutorService listener;

    public PostgresWebSocketBridge(DataSource dataSource, ObjectMapper mapper,
            SimpMessagingTemplate socket,
            @Value("${app.websocket.cluster.enabled:false}") boolean clustered) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.socket = socket;
        this.clustered = clustered;
    }

    @PostConstruct
    void start() {
        if (!clustered) return;
        running = true;
        listener = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "central-postgres-websocket");
            thread.setDaemon(true);
            return thread;
        });
        listener.execute(this::listen);
    }

    @PreDestroy
    void close() {
        running = false;
        if (listener != null) listener.shutdownNow();
    }

    public void publish(String destination, Map<String, ?> payload) {
        if (destination == null || !destination.startsWith("/topic/") || payload == null) {
            throw new IllegalArgumentException("Invalid STOMP destination or payload");
        }
        if (!clustered) {
            socket.convertAndSend(destination, payload);
            return;
        }
        Runnable afterCommit = () -> {
            try {
                String data = mapper.writeValueAsString(Map.of(
                        "destination", destination, "payload", payload));
                // Obtain a separate JDBC connection: afterCommit() still has the
                // original transaction resources bound to its thread. Reusing
                // JdbcTemplate there could execute NOTIFY without a fresh COMMIT.
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(true);
                    try (PreparedStatement statement =
                            connection.prepareStatement("SELECT pg_notify(?, ?)")) {
                        statement.setString(1, CHANNEL);
                        statement.setString(2, data);
                        statement.execute();
                    }
                }
            } catch (Exception ex) {
                // Committed orders must not fail if a transient notification fails.
                log.warn("Unable to broadcast order notification: {}", ex.toString());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommit.run();
                }
            });
        } else {
            afterCommit.run();
        }
    }

    private void listen() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN " + CHANNEL);
                }
                PGConnection pg = connection.unwrap(PGConnection.class);
                log.info("PostgreSQL WebSocket event listener connected");
                while (running && !Thread.currentThread().isInterrupted()) {
                    PGNotification[] notifications = pg.getNotifications(1000);
                    if (notifications == null) continue;
                    for (PGNotification notification : notifications) {
                        try {
                            JsonNode event = mapper.readTree(notification.getParameter());
                            String destination = event.path("destination").asText("");
                            JsonNode payload = event.path("payload");
                            if (destination.startsWith("/topic/") && payload.isObject()) {
                                socket.convertAndSend(destination, mapper.convertValue(payload, Map.class));
                            }
                        } catch (Exception ex) {
                            log.warn("Invalid PostgreSQL WebSocket event: {}", ex.toString());
                        }
                    }
                }
            } catch (Exception ex) {
                if (!running) break;
                log.warn("PostgreSQL WebSocket listener disconnected; retrying: {}", ex.toString());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
