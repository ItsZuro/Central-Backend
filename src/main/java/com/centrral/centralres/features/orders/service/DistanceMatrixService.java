package com.centrral.centralres.features.orders.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Consulta Google Routes API (Compute Routes). Distance Matrix Legacy no está
 * disponible para proyectos Google Maps nuevos. La respuesta se adapta al
 * formato previamente consumido por DeliveryQuoteService.
 *
 * La API key solo se utiliza en el backend mediante GOOGLE_MAPS_API_KEY.
 */
@Service
public class DistanceMatrixService {

    private static final String ROUTES_URL =
            "https://routes.googleapis.com/directions/v2:computeRoutes";

    private final RestTemplate http;

    @Value("${google.maps.api-key}")
    private String apiKey;

    public DistanceMatrixService() {
        this(new RestTemplate());
    }

    // Constructor de pruebas: permite simular Google sin emitir solicitudes reales.
    DistanceMatrixService(RestTemplate http) {
        this.http = http;
    }

    public Map<String, Object> getDistanceAndDuration(double originLat, double originLng,
            double destinationLat, double destinationLng) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("La clave de Routes API no está configurada.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "routes.distanceMeters,routes.duration");

        Map<String, Object> requestBody = Map.of(
                "origin", Map.of("location", Map.of("latLng", Map.of(
                        "latitude", originLat, "longitude", originLng))),
                "destination", Map.of("location", Map.of("latLng", Map.of(
                        "latitude", destinationLat, "longitude", destinationLng))),
                "travelMode", "DRIVE",
                "routingPreference", "TRAFFIC_UNAWARE");

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = http.exchange(
                    ROUTES_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    Map.class);

            return normalizeRoutesResponse(response.getBody());
        } catch (RestClientException ex) {
            // No reenviar al cliente el detalle bruto de Google ni la API key.
            throw new IllegalStateException(
                    "No se pudo consultar Google Routes. Revisa permisos, facturación y configuración de la API.",
                    ex);
        }
    }

    /**
     * Convierte la respuesta REST de Routes al formato de Distance Matrix
     * consumido por el servicio de cobertura, sin cambiar sus reglas de negocio.
     */
    static Map<String, Object> normalizeRoutesResponse(Map<?, ?> response) {
        if (response == null || !(response.get("routes") instanceof List<?> routes)
                || routes.isEmpty()) {
            return Map.of("status", "ZERO_RESULTS");
        }

        if (!(routes.get(0) instanceof Map<?, ?> route)
                || !(route.get("distanceMeters") instanceof Number meters)
                || !(route.get("duration") instanceof String duration)
                || !duration.endsWith("s")) {
            throw new IllegalStateException("Google Routes devolvió una ruta incompleta.");
        }

        double seconds;
        try {
            seconds = new BigDecimal(duration.substring(0, duration.length() - 1))
                    .doubleValue();
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Google Routes devolvió una duración inválida.", ex);
        }
        if (meters.doubleValue() < 0 || !Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalStateException("Google Routes devolvió una ruta inválida.");
        }

        return Map.of(
                "status", "OK",
                "rows", List.of(Map.of(
                        "elements", List.of(Map.of(
                                "status", "OK",
                                "distance", Map.of("value", meters.doubleValue()),
                                "duration", Map.of("value", seconds))))));
    }
}
