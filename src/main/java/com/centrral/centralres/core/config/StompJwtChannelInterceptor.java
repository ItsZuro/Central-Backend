package com.centrral.centralres.core.config;

import java.security.Principal;
import java.util.Set;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import com.centrral.centralres.core.security.jwt.JwtService;
import com.centrral.centralres.core.security.service.TokenBlacklistService;

import lombok.RequiredArgsConstructor;

/** Authenticate STOMP independently from the public HTTP upgrade. */
@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptor implements ChannelInterceptor {
    private static final Set<String> STAFF_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CHEF",
            "ROLE_WAITER", "ROLE_CASHIER", "ROLE_DRIVER");

    private final JwtService jwtService;
    private final TokenBlacklistService blacklist;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) return message;

        StompCommand command = headers.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            authenticate(headers);
        } else if (command == StompCommand.SUBSCRIBE) {
            requireStaff(headers.getUser());
            if (!"/topic/orders".equals(headers.getDestination())) {
                throw new AccessDeniedException("Suscripcion STOMP no autorizada.");
            }
        } else if (command == StompCommand.SEND) {
            throw new AccessDeniedException("El cliente no puede publicar eventos STOMP.");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor headers) {
        String authorization = headers.getFirstNativeHeader("Authorization");
        if (authorization == null) authorization = headers.getFirstNativeHeader("authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("Falta JWT en STOMP CONNECT.");
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty() || !jwtService.validate(token)) {
            throw new AccessDeniedException("JWT invalido o expirado.");
        }
        if (blacklist.isBlacklisted(token)) throw new AccessDeniedException("Sesion revocada.");

        String username = jwtService.extractSubject(token);
        if (username == null || username.isBlank()
                || blacklist.tokenPredatesStaffSessionRevocation(
                        username, jwtService.getIssuedAtInstant(token))) {
            throw new AccessDeniedException("Sesion no autorizada.");
        }
        UserDetails user = userDetailsService.loadUserByUsername(username);
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw new AccessDeniedException("Cuenta deshabilitada o bloqueada.");
        }
        Authentication principal = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
        requireStaff(principal);
        headers.setUser(principal);
    }

    private static void requireStaff(Principal principal) {
        if (!(principal instanceof Authentication auth)
                || !auth.isAuthenticated()
                || auth.getAuthorities().stream()
                    .noneMatch(role -> STAFF_ROLES.contains(role.getAuthority()))) {
            throw new AccessDeniedException("Solo personal autorizado puede suscribirse.");
        }
    }
}
