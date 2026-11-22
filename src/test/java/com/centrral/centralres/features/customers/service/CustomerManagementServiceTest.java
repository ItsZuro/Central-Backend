package com.centrral.centralres.features.customers.service;

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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import com.centrral.centralres.core.exceptions.BadRequestException;
import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.core.security.service.AuthService;
import com.centrral.centralres.core.security.service.TokenBlacklistService;
import com.centrral.centralres.features.customers.dto.customer.request.CustomerAccountStatusRequest;
import com.centrral.centralres.features.customers.dto.customer.response.CustomerResponse;
import com.centrral.centralres.features.customers.model.Customer;
import com.centrral.centralres.features.customers.model.CustomerAccountAction;
import com.centrral.centralres.features.customers.repository.CustomerAccountActionRepository;
import com.centrral.centralres.features.customers.repository.CustomerRepository;

class CustomerManagementServiceTest {
    @Mock CustomerRepository customerRepository;
    @Mock CustomerService customerService;
    @Mock UserRepository userRepository;
    @Mock CustomerAccountActionRepository actions;
    @Mock AuthService authService;
    @Mock TokenBlacklistService tokenBlacklistService;
    @InjectMocks CustomerManagementService service;

    private User manager;
    private User client;
    private Customer customer;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        manager = User.builder().id(10L).username("gerente").enabled(true)
                .roles(Set.of(Role.builder().name("ROLE_MANAGER").build())).build();
        client = User.builder().id(20L).username("cliente").enabled(true)
                .roles(Set.of(Role.builder().name("ROLE_CLIENT").build())).build();
        customer = new Customer();
        customer.setId(30L);
        customer.setUser(client);
        when(customerRepository.findById(30L)).thenReturn(Optional.of(customer));
        when(customerService.getCustomer(30L))
                .thenAnswer(call -> CustomerResponse.builder().id(30L).enabled(client.isEnabled()).build());
    }

    private CustomerAccountStatusRequest request(boolean enabled, String reason) {
        CustomerAccountStatusRequest request = new CustomerAccountStatusRequest();
        request.setEnabled(enabled);
        request.setReason(reason);
        return request;
    }

    @Test
    void managerCanSuspendWithAuditAndSessionRevocation() {
        var result = service.changeStatus(30L, request(false, "Solicitud documentada de seguridad."), manager);

        assertThat(result.getEnabled()).isFalse();
        assertThat(client.isEnabled()).isFalse();
        verify(userRepository).save(client);
        verify(authService).revokeAllRefreshTokens(client);
        verify(tokenBlacklistService).recordCustomerAccountSuspension("cliente");
        ArgumentCaptor<CustomerAccountAction> recorded = ArgumentCaptor.forClass(CustomerAccountAction.class);
        verify(actions).save(recorded.capture());
        assertThat(recorded.getValue().getActorUserId()).isEqualTo(10L);
        assertThat(recorded.getValue().getAction()).isEqualTo("SUSPENDED");
        assertThat(recorded.getValue().getReason()).contains("seguridad");
    }

    @Test
    void reactivateKeepsOldSessionsRevoked() {
        client.setEnabled(false);
        service.changeStatus(30L, request(true, "Caso revisado y autorizado."), manager);
        assertThat(client.isEnabled()).isTrue();
        verify(authService, never()).revokeAllRefreshTokens(any(User.class));
        verify(tokenBlacklistService, never()).recordCustomerAccountSuspension("cliente");
    }

    @Test
    void ordinaryCustomerCannotSuspendAnyone() {
        assertThatThrownBy(() -> service.changeStatus(30L,
                request(false, "Razón de prueba suficientemente larga"), client))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void cannotSuspendWithoutValidReason() {
        assertThatThrownBy(() -> service.changeStatus(30L, request(false, "corto"), manager))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void cannotSuspendAlreadySuspendedCustomer() {
        client.setEnabled(false);
        assertThatThrownBy(() -> service.changeStatus(30L,
                request(false, "Solicitud documentada de seguridad"), manager))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any(User.class));
    }
}
