package com.centrral.centralres.core.security.init;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/** Creates the independent delivery role without modifying existing accounts. */
@Component
@Profile({ "dev", "prod" })
@Order(2)
@RequiredArgsConstructor
public class DriverRoleInitializer implements CommandLineRunner {
    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        roleRepository.findByName("ROLE_DRIVER")
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_DRIVER")));
    }
}
