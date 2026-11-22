package com.centrral.centralres.features.customers.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.RoleRepository;
import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.features.customers.model.Customer;
import com.centrral.centralres.features.customers.repository.CustomerRepository;
import com.centrral.centralres.features.feedbackloyalty.service.LoyaltyService;
import com.centrral.centralres.features.feedbackloyalty.service.PointsHistoryService;

class CustomerReadPermissionsTest {
    @Mock CustomerRepository customerRepository;
    @Mock UserRepository userRepository;
    @Mock LoyaltyService loyaltyService;
    @Mock PointsHistoryService pointsHistoryService;
    @Mock FileService fileService;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks CustomerService service;

    private User owner;
    private User anotherClient;
    private User manager;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        owner = User.builder().id(10L).username("owner").roles(
                Set.of(Role.builder().name("ROLE_CLIENT").build())).build();
        anotherClient = User.builder().id(11L).username("another").roles(
                Set.of(Role.builder().name("ROLE_CLIENT").build())).build();
        manager = User.builder().id(12L).username("manager").roles(
                Set.of(Role.builder().name("ROLE_MANAGER").build())).build();
        Customer customer = new Customer();
        customer.setId(7L);
        customer.setUser(owner);
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
    }

    @Test
    void ownerMayAccessTheirOwnCustomerProfile() {
        assertThatCode(() -> service.requireCustomerOwnerOrManagement(7L, owner))
                .doesNotThrowAnyException();
    }

    @Test
    void anotherClientCannotAccessCustomerProfile() {
        assertThatThrownBy(() -> service.requireCustomerOwnerOrManagement(7L, anotherClient))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void managerMayReadCustomerProfiles() {
        assertThatCode(() -> service.requireCustomerOwnerOrManagement(7L, manager))
                .doesNotThrowAnyException();
    }

    @Test
    void managerCannotChangeCustomerContactCredentialsThroughLegacyEndpoint() {
        assertThatThrownBy(() -> service.requireCustomerOwner(7L, manager))
                .isInstanceOf(AccessDeniedException.class);
    }
}
