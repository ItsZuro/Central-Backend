package com.centrral.centralres.features.employees.service;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centrral.centralres.features.employees.dto.schedule.request.ScheduleRequest;
import com.centrral.centralres.features.employees.dto.schedule.request.StaffShiftRequest;
import com.centrral.centralres.features.employees.dto.schedule.response.ScheduleResponse;
import com.centrral.centralres.features.employees.enums.DayOfWeekEnum;
import com.centrral.centralres.features.employees.model.Employee;
import com.centrral.centralres.features.employees.model.Schedule;
import com.centrral.centralres.features.employees.repository.EmployeeRepository;
import com.centrral.centralres.features.employees.repository.ScheduleRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

        private final ScheduleRepository scheduleRepository;
        private final EmployeeRepository employeeRepository;
        private static final ZoneId RESTAURANT_ZONE_ID = ZoneId.of("America/Lima");

        @Transactional(readOnly = true)
        public List<ScheduleResponse> getAll() {
                return scheduleRepository.findAll()
                                .stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public ScheduleResponse getById(Long id) {
                Schedule schedule = scheduleRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Horario no encontrado con id: " + id));
                return mapToResponse(schedule);
        }

        @Transactional
        public ScheduleResponse create(ScheduleRequest request) {
                Employee employee = employeeRepository.findById(request.getEmployeeId())
                                .orElseThrow(
                                                () -> new EntityNotFoundException("Empleado no encontrado con id: "
                                                                + request.getEmployeeId()));

                Schedule schedule = Schedule.builder()
                                .employee(employee)
                                .dayOfWeek(request.getDayOfWeek())
                                .startTime(request.getStartTime())
                                .endTime(request.getEndTime())
                                .build();

                return mapToResponse(scheduleRepository.save(schedule));
        }

        @Transactional
        public ScheduleResponse update(Long id, ScheduleRequest request) {
                Schedule schedule = scheduleRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Horario no encontrado con id: " + id));

                Employee employee = employeeRepository.findById(request.getEmployeeId())
                                .orElseThrow(
                                                () -> new EntityNotFoundException("Empleado no encontrado con id: "
                                                                + request.getEmployeeId()));

                schedule.setEmployee(employee);
                schedule.setDayOfWeek(request.getDayOfWeek());
                schedule.setStartTime(request.getStartTime());
                schedule.setEndTime(request.getEndTime());

                return mapToResponse(scheduleRepository.save(schedule));
        }

        @Transactional
        public void delete(Long id) {
                Schedule schedule = scheduleRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Horario no encontrado con id: " + id));
                scheduleRepository.delete(schedule);
        }

        /**
         * Devuelve los turnos del empleado asociado a una cuenta. Solo debe invocarse
         * desde operaciones que ya validaron privilegios de administrador.
         */
        @Transactional(readOnly = true)
        public List<StaffShiftRequest> getWeeklyScheduleForUser(Long userId) {
                Employee employee = employeeRepository.findByUserId(userId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "El usuario no tiene un perfil de empleado asociado."));
                return scheduleRepository.findByEmployeeId(employee.getId()).stream()
                                .sorted((a, b) -> a.getDayOfWeek().compareTo(b.getDayOfWeek()))
                                .map(this::toStaffShift)
                                .toList();
        }

        /**
         * Reemplazo completo y atómico: los días omitidos se consideran libres.
         * No se permiten horarios vacíos, días duplicados ni turnos nocturnos
         * que crucen medianoche, porque la validación de login es por día.
         */
        @Transactional
        public List<StaffShiftRequest> replaceWeeklyScheduleForUser(Long userId,
                                                                     List<StaffShiftRequest> requested) {
                if (requested == null || requested.isEmpty() || requested.size() > 7) {
                        throw new IllegalArgumentException("Selecciona entre uno y siete días laborales.");
                }
                Set<DayOfWeekEnum> seenDays = new HashSet<>();
                for (StaffShiftRequest shift : requested) {
                        if (shift == null || shift.getDayOfWeek() == null
                                        || shift.getStartTime() == null || shift.getEndTime() == null) {
                                throw new IllegalArgumentException("Completa los días y las horas del horario.");
                        }
                        if (!seenDays.add(shift.getDayOfWeek())) {
                                throw new IllegalArgumentException("No se puede repetir un día de la semana.");
                        }
                        if (!shift.getStartTime().isBefore(shift.getEndTime())) {
                                throw new IllegalArgumentException(
                                                "La hora de salida debe ser posterior a la de entrada en cada día.");
                        }
                }

                Employee employee = employeeRepository.findByUserId(userId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "El usuario no tiene un perfil de empleado asociado."));
                scheduleRepository.deleteAll(scheduleRepository.findByEmployeeId(employee.getId()));
                scheduleRepository.flush();

                List<Schedule> newSchedules = requested.stream()
                                .map(shift -> Schedule.builder()
                                                .employee(employee)
                                                .dayOfWeek(shift.getDayOfWeek())
                                                .startTime(shift.getStartTime())
                                                .endTime(shift.getEndTime())
                                                .build())
                                .toList();
                scheduleRepository.saveAll(newSchedules);
                scheduleRepository.flush();
                return getWeeklyScheduleForUser(userId);
        }

        private StaffShiftRequest toStaffShift(Schedule schedule) {
                StaffShiftRequest response = new StaffShiftRequest();
                response.setDayOfWeek(schedule.getDayOfWeek());
                response.setStartTime(schedule.getStartTime());
                response.setEndTime(schedule.getEndTime());
                return response;
        }

        public boolean isWithinSchedule(Employee employee) {

                ZonedDateTime nowZoned = ZonedDateTime.now(RESTAURANT_ZONE_ID);
                LocalTime now = nowZoned.toLocalTime();
                java.time.DayOfWeek javaDay = nowZoned.getDayOfWeek();
                DayOfWeekEnum dayOfWeek;

                log.info("--- Verificación de Horario para Employee ID: {} ---", employee.getId());
                log.info("Hora actual (America/Lima): {} | Día: {}", now, javaDay.name());

                try {
                        dayOfWeek = DayOfWeekEnum.valueOf(javaDay.name());
                } catch (IllegalArgumentException e) {
                        log.error("Error al mapear el día de la semana: {}", javaDay.name());
                        return false;
                }

                var scheduleOpt = employee.getSchedules().stream()
                                .filter(s -> s.getDayOfWeek() == dayOfWeek)
                                .findFirst();

                if (scheduleOpt.isEmpty()) {
                        log.warn("No se encontró horario para el día {}", dayOfWeek);
                        log.info("-----------------------------------------------------");
                        return false;
                }

                Schedule todaySchedule = scheduleOpt.get();
                LocalTime startTime = todaySchedule.getStartTime();
                LocalTime endTime = todaySchedule.getEndTime();

                log.info("Horario encontrado para {}: [{} - {}]", dayOfWeek, startTime, endTime);

                boolean onOrAfterStart = !now.isBefore(startTime);
                boolean onOrBeforeEnd = !now.isAfter(endTime);

                boolean result = onOrAfterStart && onOrBeforeEnd;

                log.info("Comprobación: (now >= startTime) -> {} | (now <= endTime) -> {}", onOrAfterStart,
                                onOrBeforeEnd);
                log.info("Resultado final de la comprobación: {}", result);
                log.info("-----------------------------------------------------");

                return result;
        }

        private ScheduleResponse mapToResponse(Schedule schedule) {
                return ScheduleResponse.builder()
                                .id(schedule.getId())
                                .employeeId(schedule.getEmployee().getId())
                                .employeeName(schedule.getEmployee().getUser().getFullName())
                                .positionName(schedule.getEmployee().getPosition().getName())
                                .dayOfWeek(schedule.getDayOfWeek())
                                .startTime(schedule.getStartTime())
                                .endTime(schedule.getEndTime())
                                .build();
        }
}
