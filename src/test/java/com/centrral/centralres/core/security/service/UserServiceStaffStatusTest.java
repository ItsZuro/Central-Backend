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
import com.centrral.centralres.core.security.dto.StaffAccountStatusRequest;
import com.centrral.centralres.core.security.enums.AuthProvider;
import com.centrral.centralres.core.security.jwt.JwtService;
import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.RoleRepository;
import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.features.employees.service.ScheduleService;
import com.centrral.centralres.features.employees.service.StaffEmployeeProvisioner;

class UserServiceStaffStatusTest {
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

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        admin = User.builder().id(1L).username("admin").email("admin@central.test")
                .firstName("Admin").lastName("Central").enabled(true).provider(AuthProvider.LOCAL)
                .roles(Set.of(Role.builder().name("ROLE_ADMIN").build())).build();
        waiter = User.builder().id(2L).username("mesero.central").email("mesero@central.test")
                .firstName("Carlos").lastName("Ramírez").enabled(true).provider(AuthProvider.LOCAL)
                .roles(Set.of(Role.builder().name("ROLE_WAITER").build())).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(waiter));
        when(userRepository.findByUsername("mesero.central")).thenReturn(Optional.of(waiter));
    }

    private StaffAccountStatusRequest status(boolean enabled) {
        StaffAccountStatusRequest request = new StaffAccountStatusRequest();
        request.setEnabled(enabled);
        return request;
    }

    @Test
    void deactivationRevokesRefreshAndAccessAndKeepsAccount() {
        service.changeStaffAccountStatus(admin, 2L, status(false));

        assertThat(waiter.isEnabled()).isFalse();
        verify(userRepository).save(waiter);
        verify(authService).revokeAllRefreshTokens(waiter);
        verify(tokenBlacklistService).revokeIssuedAccessTokensForUser(
                "mesero.central", "Baja administrativa de cuenta de personal");
        verify(tokenBlacklistService).recordStaffAccountDeactivation("mesero.central");
    }

    @Test
    void reactivationDoesNotRestoreRevokedSessions() {
        waiter.setEnabled(false);
        service.changeStaffAccountStatus(admin, 2L, status(true));

        assertThat(waiter.isEnabled()).isTrue();
        verify(userRepository).save(waiter);
        verify(authService, never()).revokeAllRefreshTokens(any(User.class));
        verify(tokenBlacklistService, never()).recordStaffAccountDeactivation("mesero.central");
    }

    @Test
    void aWaiterCannotDeactivateAccounts() {
        assertThatThrownBy(() -> service.changeStaffAccountStatus(waiter, 2L, status(false)))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void anAdministratorCannotDeactivateTheirOwnAccount() {
        assertThatThrownBy(() -> service.changeStaffAccountStatus(admin, 1L, status(false)))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any(User.class));
    }
}
