package com.centrral.centralres.features.employees.init;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.centrral.centralres.features.employees.service.StaffEmployeeProvisioner;

import lombok.RequiredArgsConstructor;

/**
 * Backfill idempotente para cuentas de personal ya creadas desde Administración.
 * Se ejecuta después de los inicializadores de desarrollo y también en producción.
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class StaffEmployeeBootstrap implements CommandLineRunner {
    private final StaffEmployeeProvisioner provisioner;

    @Override
    public void run(String... args) {
        provisioner.backfillExistingStaff();
    }
}
