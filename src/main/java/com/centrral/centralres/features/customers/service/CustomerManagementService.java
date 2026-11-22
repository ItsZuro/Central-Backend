package com.centrral.centralres.features.customers.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centrral.centralres.core.dto.response.PagedResponse;
import com.centrral.centralres.core.exceptions.BadRequestException;
import com.centrral.centralres.core.exceptions.ResourceNotFoundException;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.core.security.service.AuthService;
import com.centrral.centralres.core.security.service.TokenBlacklistService;
import com.centrral.centralres.features.customers.dto.customer.request.CustomerAccountStatusRequest;
import com.centrral.centralres.features.customers.dto.customer.response.CustomerAccountActionResponse;
import com.centrral.centralres.features.customers.dto.customer.response.CustomerManagementSummary;
import com.centrral.centralres.features.customers.dto.customer.response.CustomerResponse;
import com.centrral.centralres.features.customers.model.Customer;
import com.centrral.centralres.features.customers.model.CustomerAccountAction;
import com.centrral.centralres.features.customers.repository.CustomerAccountActionRepository;
import com.centrral.centralres.features.customers.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerManagementService {
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final UserRepository userRepository;
    private final CustomerAccountActionRepository actions;
    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional(readOnly = true)
    public PagedResponse<CustomerResponse> findCustomers(String search, Boolean enabled, Pageable pageable) {
        String normalized = search == null || search.isBlank()
                ? null : search.trim().toLowerCase(Locale.ROOT);
        Page<Customer> page = customerRepository.findForManagement(normalized, enabled, pageable);
        List<CustomerResponse> customers = page.getContent().stream()
                .map(customer -> customerService.getCustomer(customer.getId())).toList();
        return PagedResponse.<CustomerResponse>builder()
                .content(customers)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public CustomerManagementSummary summary() {
        long active = customerRepository.countByUser_EnabledTrue();
        long suspended = customerRepository.countByUser_EnabledFalse();
        return new CustomerManagementSummary(active + suspended, active, suspended);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long customerId) {
        return customerService.getCustomer(customerId);
    }

    @Transactional(readOnly = true)
    public List<CustomerAccountActionResponse> history(Long customerId) {
        ensureCustomer(customerId);
        return actions.findTop20ByCustomerIdOrderByOccurredAtDesc(customerId).stream()
                .map(a -> new CustomerAccountActionResponse(
                        a.getId(), a.getActorUserId(), a.getAction(), a.getReason(), a.getOccurredAt()))
                .toList();
    }

    /**
     * Reversible account suspension. Never modifies password, checkout, purchases,
     * loyalty points, reservations or historical orders.
     */
    @Transactional
    public CustomerResponse changeStatus(Long customerId, CustomerAccountStatusRequest request, User actor) {
        if (actor == null || !(actor.hasRole("ROLE_ADMIN") || actor.hasRole("ROLE_MANAGER"))) {
            throw new AccessDeniedException("No tienes autorización para administrar clientes.");
        }
        if (request == null || request.getEnabled() == null
                || request.getReason() == null || request.getReason().trim().length() < 10
                || request.getReason().trim().length() > 500) {
            throw new BadRequestException("Debes indicar una justificación entre 10 y 500 caracteres.");
        }
        Customer customer = ensureCustomer(customerId);
        User account = customer.getUser();
        if (account == null || !account.hasRole("ROLE_CLIENT")
                || account.hasRole("ROLE_ADMIN") || account.hasRole("ROLE_MANAGER")) {
            throw new AccessDeniedException("Solo se permiten cambios sobre cuentas de clientes.");
        }
        boolean enabled = request.getEnabled();
        if (account.isEnabled() == enabled) {
            throw new BadRequestException(enabled ? "La cuenta ya se encuentra activa." : "La cuenta ya está suspendida.");
        }

        account.setEnabled(enabled);
        userRepository.save(account);

        CustomerAccountAction action = new CustomerAccountAction();
        action.setCustomerId(customerId);
        action.setActorUserId(actor.getId());
        action.setAction(enabled ? "REACTIVATED" : "SUSPENDED");
        action.setReason(request.getReason().trim());
        action.setOccurredAt(Instant.now());
        actions.save(action);

        if (!enabled) {
            authService.revokeAllRefreshTokens(account);
            tokenBlacklistService.revokeIssuedAccessTokensForUser(
                    account.getUsername(), "Suspensión administrativa de cuenta de cliente");
            tokenBlacklistService.recordCustomerAccountSuspension(account.getUsername());
        }
        log.info("Estado de cuenta de cliente modificado actorId={} customerId={} enabled={}",
                actor.getId(), customerId, enabled);
        return customerService.getCustomer(customerId);
    }

    private Customer ensureCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado."));
    }
}
