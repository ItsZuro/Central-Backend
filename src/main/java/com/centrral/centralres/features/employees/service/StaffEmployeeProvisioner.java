package com.centrral.centralres.features.employees.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.features.employees.enums.DayOfWeekEnum;
import com.centrral.centralres.features.employees.enums.EmploymentStatus;
import com.centrral.centralres.features.employees.model.Employee;
import com.centrral.centralres.features.employees.model.Position;
import com.centrral.centralres.features.employees.model.Schedule;
import com.centrral.centralres.features.employees.repository.EmployeeRepository;
import com.centrral.centralres.features.employees.repository.PositionRepository;
import com.centrral.centralres.features.employees.repository.ScheduleRepository;

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
    private final ScheduleRepository scheduleRepository;

    // Horario inicial de pruebas del restaurante: Perú, desde mediodía hasta las 23:59:59.
    // Se genera solamente cuando el empleado todavía no tiene ningún turno asignado.
    private static final LocalTime INITIAL_START = LocalTime.NOON;
    private static final LocalTime INITIAL_END = LocalTime.of(23, 59, 59);

    /**
     * La creación de usuarios de personal debe incluir su perfil operativo Employee:
     * POS y reparto consultan EmployeeRepository por el userId autenticado.
     * No se asigna sueldo ficticio ni se alteran roles existentes.
     */
    @Transactional
    public void provision(User user, String roleName) {
        if (user == null || user.getId() == null || !POSITION_DESCRIPTIONS.containsKey(roleName)) {
            return;
        }

        var existingEmployee = employeeRepository.findByUserId(user.getId());
        if (existingEmployee.isPresent()) {
            ensureInitialSchedule(existingEmployee.get());
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

        Employee savedEmployee = employeeRepository.save(employee);
        ensureInitialSchedule(savedEmployee);
        log.info("Perfil operativo Employee y horario inicial asociados a usuario de personal: {}", user.getId());
    }

    /**
     * Horario inicial solo para perfiles nuevos o perfiles existentes sin turnos.
     * No sobrescribe turnos configurados (incluidos días libres).
     * La zona horaria de evaluación se define en ScheduleService: America/Lima.
     */
    private void ensureInitialSchedule(Employee employee) {
        if (!scheduleRepository.findByEmployeeId(employee.getId()).isEmpty()) {
            return;
        }

        List<Schedule> initialSchedules = java.util.Arrays.stream(DayOfWeekEnum.values())
                .map(day -> Schedule.builder()
                        .employee(employee)
                        .dayOfWeek(day)
                        .startTime(INITIAL_START)
                        .endTime(INITIAL_END)
                        .build())
                .toList();

        scheduleRepository.saveAll(initialSchedules);
        log.info("Se configuró un horario inicial de prueba (12:00-23:59:59, lunes a domingo) para empleado {}",
                employee.getId());
    }

    /**
     * Completa perfiles y turnos faltantes de las cuentas ya creadas desde
     * Administración sin tocar horarios existentes ni cuentas de clientes.
     */
    @Transactional
    public void backfillExistingStaff() {
        for (User user : userRepository.findAll()) {
            if (!user.isEnabled()) {
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
