package com.centrral.centralres.features.orders.dto.order.response;

/**
 * Datos mínimos para asignar repartidores, sin salarios ni información privada.
 * employeeId es el identificador requerido por /orders/{id}/assign-driver.
 */
public record DriverOptionResponse(Long employeeId, String fullName, String username) {}
