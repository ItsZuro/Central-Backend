package com.centrral.centralres.features.orders.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.centrral.centralres.core.config.NiubizConfig;
import com.centrral.centralres.features.orders.dto.niubiz.NiubizDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NiubizService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final NiubizConfig niubizConfig;

    // 1. Obtener Token de Seguridad (API v1)
    public String getSecurityToken() {
        String url = niubizConfig.getApi().getSecurity(); 
        HttpHeaders headers = new HttpHeaders();
        // La documentación dice que devuelve text/plain, aceptamos todo
        headers.setAccept(Collections.singletonList(MediaType.ALL));

        String authString = niubizConfig.getMerchant().getUser() + ":" + niubizConfig.getMerchant().getPassword();
        String encodedAuth = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);

        // Body vacío obligatorio en v1? A veces sí, enviamos mapa vacío.
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(new HashMap<>(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            // NOTA: La documentación dice GET para v1 en el ejemplo (C.2 Request), 
            // aunque usualmente es POST. Si falla con GET, cámbialo a POST. 
            // Tu ejemplo dice: GET /api.security/v1/security
            
            String responseBody = response.getBody();

            if (responseBody == null) throw new RuntimeException("Niubiz devolvió body vacío");

            // Limpieza robusta del token
            if (responseBody.contains("accessToken")) {
                 // Si devuelve JSON (aunque la doc dice text/plain, a veces cambia)
                responseBody = responseBody
                        .replace("{", "")
                        .replace("}", "")
                        .replace("\"", "")
                        .replace("accessToken:", "")
                        .trim();
            } else {
                // Si es texto plano
                responseBody = responseBody.replace("\"", "").trim();
            }
            return responseBody;

        } catch (HttpClientErrorException e) {
             // Si falla con GET, intentamos POST (Fallback)
             if(e.getStatusCode().value() == 405) {
                 log.info("GET no permitido para Security, intentando POST...");
                 return getSecurityTokenPostFallback(url, entity);
             }
             log.error("Error Seguridad Niubiz: {}", e.getResponseBodyAsString());
             throw e;
        } catch (Exception e) {
            log.error("Error obteniendo token Niubiz", e);
            throw new RuntimeException("Error de autenticación con Niubiz.");
        }
    }

    private String getSecurityTokenPostFallback(String url, HttpEntity<?> entity) {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return response.getBody().replace("\"", "").trim(); // Lógica simplificada para fallback
    }

    // 2. Crear Sesión (API v2)
    public NiubizDTO.PaymentStartResponse createPaymentSession(BigDecimal amount, String purchaseNumber) {
        String token = getSecurityToken();

        HttpHeaders headers = new HttpHeaders();
        // Niubiz V2 (Sandbox) espera el token CRUDO en el header Authorization
        headers.set("Authorization", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("channel", "web");
        body.put("amount", amount);

        Map<String, Object> antifraud = new HashMap<>();
        // Datos obligatorios según tu documentación
        antifraud.put("clientIp", "24.252.107.29"); // IP Pública de ejemplo válida

        Map<String, Object> mdd = new HashMap<>();
        mdd.put("MDD4", "integraciones@niubiz.com.pe");
        mdd.put("MDD32", "JD1892639123");
        mdd.put("MDD75", "Registrado");
        mdd.put("MDD77", 1);

        antifraud.put("merchantDefineData", mdd);
        body.put("antifraud", antifraud);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            // Asegúrate de que en el YAML 'session' NO tenga /{merchantId} al final
            String url = niubizConfig.getApi().getSession() + "/" + niubizConfig.getMerchant().getId();
            log.info("URL Sesión: {}", url);

            // Usamos la nueva clase interna SessionResponse para recibir el JSON
            ResponseEntity<NiubizDTO.SessionResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, NiubizDTO.SessionResponse.class);

            if (response.getBody() == null) {
                throw new RuntimeException("Respuesta de Niubiz vacía");
            }

            // CAMBIO AQUÍ: Usamos getSessionKey()
            log.info("Sesión de pago Niubiz creada correctamente.");

            return NiubizDTO.PaymentStartResponse.builder()
                    .sessionKey(response.getBody().getSessionKey()) // <--- AQUÍ EL CAMBIO
                    .expirationTime(response.getBody().getExpirationTime())
                    .merchantId(niubizConfig.getMerchant().getId())
                    .purchaseNumber(purchaseNumber)
                    .amount(amount)
                    .build();

        } catch (HttpClientErrorException e) {
            log.error("Error HTTP Niubiz {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error Niubiz: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error general sesión Niubiz", e);
            throw new RuntimeException("Error al iniciar la pasarela de pagos.");
        }
    }

    // 3. Autorizar Pago (API v3)
    public boolean authorizePayment(String transactionToken, BigDecimal amount, String purchaseNumber) {
        String token = getSecurityToken();
        
        HttpHeaders headers = new HttpHeaders();
        // Sin Bearer aquí también
        headers.set("Authorization", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("channel", "web");
        body.put("captureType", "manual"); // Doc dice "manual"
        body.put("countable", true);

        Map<String, Object> order = new HashMap<>();
        order.put("tokenId", transactionToken);
        order.put("purchaseNumber", purchaseNumber);
        order.put("amount", amount);
        order.put("currency", "PEN");

        body.put("order", order);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            String url = niubizConfig.getApi().getAuthorization() + "/" + niubizConfig.getMerchant().getId();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("dataMap")) {
                Map dataMap = (Map) response.getBody().get("dataMap");
                // Verificar si ACTION_CODE es "000" (Aprobado)
                return "000".equals(dataMap.get("ACTION_CODE"));
            }
            return false;
        } catch (Exception e) {
            log.error("Error autorizando", e);
            return false;
        }
    }
}