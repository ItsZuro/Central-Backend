package com.centrral.centralres.features.employees.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.features.employees.enums.EmploymentStatus;
import com.centrral.centralres.features.employees.model.Employee;
import com.centrral.centralres.features.employees.model.Position;
import com.centrral.centralres.features.employees.repository.EmployeeRepository;
import com.centrral.centralres.features.employees.repository.PositionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffEmployeeProvisioner {

    private static final List<String> STAFF_ROLE_PRIORITY =
            List.of("ROLE_MANAGER", "ROLE_WAITER", "ROLE_CHEF", "ROLE_CASHIER");

    private static final Map<String, String> POSITION_DESCRIPTIONS = Map.of(
            "ROLE_MANAGER", "Gerente del restaurante",
            "ROLE_WAITER", "Mesero - atención de sala y reparto",
            "ROLE_CHEF", "Personal de cocina",
            "ROLE_CASHIER", "Personal de caja");

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;

    /**
     * La creación de usuarios de personal debe incluir su perfil operativo Employee:
     * POS y reparto consultan EmployeeRepository por el userId autenticado.
     * No se asigna sueldo ficticio ni se alteran roles existentes.
     */
    @Transactional
    public void provision(User user, String roleName) {
        if (user == null || user.getId() == null
                || !POSITION_DESCRIPTIONS.containsKey(roleName)
                || employeeRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }

        Role role = user.getRoles().stream()
                .filter(item -> roleName.equals(item.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El usuario no tiene el rol de personal indicado."));

        String positionName = roleName.substring("ROLE_".length());
        Position position = positionRepository.findAll().stream()
                .filter(item -> positionName.equalsIgnoreCase(item.getName()))
                .findFirst()
                .orElseGet(() -> positionRepository.save(Position.builder()
                        .name(positionName)
                        .description(POSITION_DESCRIPTIONS.get(roleName))
                        .roles(Set.of(role))
                        .build()));

        // hireDate es obligatorio en el esquema actual. Hasta que se ingrese una
        // fecha de contratación real, la fecha de alta del perfil es la inicial.
        Employee employee = Employee.builder()
                .user(user)
                .position(position)
                .hireDate(LocalDate.now())
                .status(EmploymentStatus.ACTIVE)
                .build();

        employeeRepository.save(employee);
        log.info("Perfil operativo Employee asociado a usuario de personal: {}", user.getId());
    }

    /**
     * Recupera cuentas de personal creadas antes de que el alta administrativa
     * generase empleados. Es idempotente y no modifica clientes ni proveedores.
     */
    @Transactional
    public void backfillExistingStaff() {
        for (User user : userRepository.findAll()) {
            if (!user.isEnabled() || employeeRepository.findByUserId(user.getId()).isPresent()) {
                continue;
            }

            for (String roleName : STAFF_ROLE_PRIORITY) {
                if (user.hasRole(roleName)) {
                    provision(user, roleName);
                    break;
                }
            }
        }
    }
}
