package com.centrral.centralres.features.employees.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.centrral.centralres.features.employees.model.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByEmployeeId(Long employeeId);

}
