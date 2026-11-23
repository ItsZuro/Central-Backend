package com.centrral.centralres.core.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

class PostgresWebSocketBridgeTest {
    @Test
    void localModeDeliversEventWithoutDatabase() {
        DataSource database = Mockito.mock(DataSource.class);
        SimpMessagingTemplate socket = Mockito.mock(SimpMessagingTemplate.class);
        PostgresWebSocketBridge bridge =
                new PostgresWebSocketBridge(database, new ObjectMapper(), socket, false);
        Map<String, Object> hint = Map.of("orderId", 42L, "event", "UPDATED");

        bridge.publish("/topic/orders", hint);

        verify(socket).convertAndSend("/topic/orders", hint);
    }

    @Test
    void rejectsDestinationsOutsideTopicPrefix() {
        PostgresWebSocketBridge bridge = new PostgresWebSocketBridge(
                Mockito.mock(DataSource.class), new ObjectMapper(),
                Mockito.mock(SimpMessagingTemplate.class), false);

        assertThrows(IllegalArgumentException.class,
                () -> bridge.publish("/user/private", Map.of("id", 1)));
    }
}
