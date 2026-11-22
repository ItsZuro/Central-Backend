package com.centrral.centralres.features.orders.service;

import java.util.Comparator;

import com.centrral.centralres.core.exceptions.BadRequestException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.centrral.centralres.features.orders.dto.order.response.DeliveryQuoteResponse;
import com.centrral.centralres.features.restaurant.model.Store;
import com.centrral.centralres.features.restaurant.repository.StoreRepository;

import lombok.RequiredArgsConstructor;

/**
 * The delivery coverage is a provisional road-distance threshold. Configure
 * DELIVERY_MAX_DISTANCE_KM to match the restaurant's actual service area.
 * Never trust browser-supplied distance, duration or eligibility.
 */
@Service
@RequiredArgsConstructor
public class DeliveryQuoteService {
    private final StoreRepository storeRepository;
    private final DistanceMatrixService distanceMatrixService;

    @Value("${delivery.max-distance-km:5.0}")
    private double maxDistanceKm;

    public DeliveryQuoteResponse quote(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BadRequestException("Coordenadas de entrega inválidas.");
        }
        if (maxDistanceKm <= 0 || !Double.isFinite(maxDistanceKm)) {
            throw new IllegalStateException("La cobertura de delivery no está configurada.");
        }

        Store store = storeRepository.findAll().stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .sorted(Comparator.comparing(Store::getId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No hay una sucursal con coordenadas de reparto configuradas."));

        Map<String, Object> response = distanceMatrixService.getDistanceAndDuration(
                store.getLatitude(), store.getLongitude(), latitude, longitude);
        if (response != null && "ZERO_RESULTS".equals(response.get("status"))) {
            throw new BadRequestException("No existe una ruta vehicular disponible para esta dirección.");
        }
        if (response == null || !"OK".equals(response.get("status"))) {
            throw new IllegalStateException("No se pudo verificar la ruta de entrega en Google Routes.");
        }

        Object rowsObj = response.get("rows");
        if (!(rowsObj instanceof List<?> rows) || rows.isEmpty()
                || !(rows.get(0) instanceof Map<?, ?> firstRow)
                || !(firstRow.get("elements") instanceof List<?> elements) || elements.isEmpty()
                || !(elements.get(0) instanceof Map<?, ?> element)
                || !"OK".equals(element.get("status"))
                || !(element.get("distance") instanceof Map<?, ?> distance)
                || !(element.get("duration") instanceof Map<?, ?> duration)
                || !(distance.get("value") instanceof Number meters)
                || !(duration.get("value") instanceof Number seconds)) {
            throw new BadRequestException("No existe una ruta de reparto válida para la dirección.");
        }

        double km = meters.doubleValue() / 1000.0;
        if (!Double.isFinite(km) || km < 0 || km > maxDistanceKm) {
            throw new BadRequestException("Esta dirección supera los " + maxDistanceKm + " km por carretera de cobertura.");
        }
        int minutes = Math.max(1, (int) Math.ceil(seconds.doubleValue() / 60.0));
        return new DeliveryQuoteResponse(Math.round(km * 100.0) / 100.0, minutes, maxDistanceKm);
    }
}
