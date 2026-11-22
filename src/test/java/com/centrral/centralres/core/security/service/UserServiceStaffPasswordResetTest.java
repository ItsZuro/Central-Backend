package com.centrral.centralres.core.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.centrral.centralres.core.aws.service.FileService;
import com.centrral.centralres.core.exceptions.InvalidPasswordException;
import com.centrral.centralres.core.security.dto.AdminResetStaffPasswordRequest;
import com.centrral.centralres.core.security.enums.AuthProvider;
import com.centrral.centralres.core.security.jwt.JwtService;
import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.RoleRepository;
import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.features.employees.service.ScheduleService;
import com.centrral.centralres.features.employees.service.StaffEmployeeProvisioner;

class UserServiceStaffPasswordResetTest {
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthService authService;
    @Mock JwtService jwtService;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock FileService fileService;
    @Mock StaffEmployeeProvisioner staffEmployeeProvisioner;
    @Mock ScheduleService scheduleService;
    @Mock TokenBlacklistService tokenBlacklistService;
    @InjectMocks UserService service;

    private User admin;
    private User waiter;
    private AdminResetStaffPasswordRequest request;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        admin = User.builder()
                .id(1L).username("admin").password("admin-encoded")
                .provider(AuthProvider.LOCAL).enabled(true)
                .roles(Set.of(Role.builder().name("ROLE_ADMIN").build())).build();
        waiter = User.builder()
                .id(2L).username("mesero.central").password("old-encoded")
                .provider(AuthProvider.LOCAL).enabled(true)
                .roles(Set.of(Role.builder().name("ROLE_WAITER").build())).build();
        request = new AdminResetStaffPasswordRequest();
        request.setAdminPassword("admin-password");
        request.setNewPassword("a-new-secret-password");
    }

    @Test
    void adminCanResetStaffPasswordAndRevokeAllSessions() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(waiter));
        when(passwordEncoder.matches("admin-password", "admin-encoded")).thenReturn(true);
        when(passwordEncoder.encode("a-new-secret-password")).thenReturn("new-encoded");

        service.resetStaffPassword(admin, 2L, request);

        assertThat(waiter.getPassword()).isEqualTo("new-encoded");
        verify(userRepository).save(waiter);
        verify(authService).revokeAllRefreshTokens(waiter);
        verify(tokenBlacklistService).revokeIssuedAccessTokensForUser(
                "mesero.central", "Restablecimiento de contraseña por administrador");
        verify(tokenBlacklistService).recordStaffPasswordReset("mesero.central");
    }

    @Test
    void wrongAdminPasswordDoesNotModifyEmployeeAccount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.resetStaffPassword(admin, 2L, request))
                .isInstanceOf(InvalidPasswordException.class);
        verify(userRepository, never()).save(any(User.class));
        verify(tokenBlacklistService, never()).recordStaffPasswordReset("mesero.central");
    }

    @Test
    void adminCannotResetOtherAdministrators() {
        User otherAdmin = User.builder()
                .id(3L).username("other-admin").password("other")
                .provider(AuthProvider.LOCAL).enabled(true)
                .roles(Set.of(Role.builder().name("ROLE_ADMIN").build())).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(3L)).thenReturn(Optional.of(otherAdmin));
        when(passwordEncoder.matches("admin-password", "admin-encoded")).thenReturn(true);

        assertThatThrownBy(() -> service.resetStaffPassword(admin, 3L, request))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void nonAdminCannotResetStaffPasswords() {
        assertThatThrownBy(() -> service.resetStaffPassword(waiter, 2L, request))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any(User.class));
    }
}
