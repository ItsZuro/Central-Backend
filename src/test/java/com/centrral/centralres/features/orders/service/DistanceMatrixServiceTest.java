package com.centrral.centralres.features.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DistanceMatrixServiceTest {

    @Test
    void convertsGoogleRoutesResponseToLegacyDistanceDurationShape() {
        Map<String, Object> route = Map.of(
                "distanceMeters", 3200,
                "duration", "542s");
        Map<String, Object> mapped = DistanceMatrixService.normalizeRoutesResponse(
                Map.of("routes", List.of(route)));

        assertThat(mapped.get("status")).isEqualTo("OK");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) mapped.get("rows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements =
                (List<Map<String, Object>>) rows.get(0).get("elements");
        @SuppressWarnings("unchecked")
        Map<String, Object> distance = (Map<String, Object>) elements.get(0).get("distance");
        @SuppressWarnings("unchecked")
        Map<String, Object> duration = (Map<String, Object>) elements.get(0).get("duration");

        assertThat(distance.get("value")).isEqualTo(3200.0);
        assertThat(duration.get("value")).isEqualTo(542.0);
    }

    @Test
    void emptyRoutesAreNotMislabeledAsSuccessful() {
        assertThat(DistanceMatrixService.normalizeRoutesResponse(Map.of("routes", List.of())))
                .containsEntry("status", "ZERO_RESULTS");
    }

    @Test
    void handlesFractionalSeconds() {
        var result = DistanceMatrixService.normalizeRoutesResponse(Map.of(
                "routes", List.of(Map.of(
                        "distanceMeters", 500,
                        "duration", "12.5s"))));
        assertThat(result.get("status")).isEqualTo("OK");
    }

    @Test
    void rejectsIncompleteRoutes() {
        assertThatThrownBy(() -> DistanceMatrixService.normalizeRoutesResponse(Map.of(
                "routes", List.of(Map.of("distanceMeters", 1200)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incompleta");
    }
}
