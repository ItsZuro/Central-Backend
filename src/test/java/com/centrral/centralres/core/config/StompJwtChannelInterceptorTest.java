package com.centrral.centralres.core.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.centrral.centralres.core.security.jwt.JwtService;
import com.centrral.centralres.core.security.service.TokenBlacklistService;

class StompJwtChannelInterceptorTest {
    JwtService jwt = mock(JwtService.class);
    TokenBlacklistService blacklist = mock(TokenBlacklistService.class);
    UserDetailsService users = mock(UserDetailsService.class);
    MessageChannel channel = mock(MessageChannel.class);
    StompJwtChannelInterceptor interceptor =
            new StompJwtChannelInterceptor(jwt, blacklist, users);

    private Message<byte[]> frame(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @BeforeEach
    void prepare() {
        when(jwt.validate("valid")).thenReturn(true);
        when(jwt.extractSubject("valid")).thenReturn("chef");
        when(jwt.getIssuedAtInstant("valid")).thenReturn(Instant.now());
        when(users.loadUserByUsername("chef")).thenReturn(
                User.withUsername("chef").password("unused").roles("CHEF").build());
    }

    @Test
    void unauthenticatedSubscribeIsDenied() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/orders"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void clientCannotSendToTopic() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SEND, "/topic/orders"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithoutJwtIsDenied() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.CONNECT, null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void staffCanConnectButCannotSubscribeElsewhere() {
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setLeaveMutable(true);
        connect.setNativeHeader("Authorization", "Bearer valid");
        interceptor.preSend(MessageBuilder.createMessage(
                new byte[0], connect.getMessageHeaders()), channel);
        org.junit.jupiter.api.Assertions.assertNotNull(connect.getUser());
        StompHeaderAccessor sub = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        sub.setLeaveMutable(true);
        sub.setUser(connect.getUser());
        sub.setDestination("/topic/orders");
        interceptor.preSend(MessageBuilder.createMessage(
                new byte[0], sub.getMessageHeaders()), channel);
        sub.setDestination("/topic/private");
        assertThatThrownBy(() -> interceptor.preSend(MessageBuilder.createMessage(
                new byte[0], sub.getMessageHeaders()), channel))
                .isInstanceOf(AccessDeniedException.class);
    }
}
