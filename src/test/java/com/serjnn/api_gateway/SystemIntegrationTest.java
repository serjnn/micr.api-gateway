package com.serjnn.api_gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

//@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class SystemIntegrationTest {

    private static final String COMPOSE_FILE = "../docker-compose.yml";
    private static final int GATEWAY_PORT = 8989;

//    @Container
//    public static DockerComposeContainer<?> environment =
//            new DockerComposeContainer<>(new File(COMPOSE_FILE))
//                    .withExposedService("api-gateway", GATEWAY_PORT,
//                            Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)))
//                    .withExposedService("product-service", 7022, Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)))
//                    .withExposedService("client-service", 7015, Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)))
//                    .withExposedService("discount-service", 7005, Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)))
//                    .withExposedService("saga-orchestrator", 7018, Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)))
//                    .withLocalCompose(true);

    private static RestClient restClient;
    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void setUp() {
//        String gatewayUrl = "http://" + environment.getServiceHost("api-gateway", GATEWAY_PORT)
//                + ":" + environment.getServicePort("api-gateway", GATEWAY_PORT);
        String gatewayUrl = "http://localhost:9000";
        restClient = RestClient.builder()
                .baseUrl(gatewayUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Test
    public void testFullSystemFlow() throws Exception {
        // 1. Register Client
        String email = "test" + System.currentTimeMillis() + "@example.com";
        Map<String, String> credentials = Map.of("mail", email, "password", "password123");
        
        restClient.post()
                .uri("/client/api/v1/clients/register")
                .body(credentials)
                .retrieve()
                .toBodilessEntity();

        // 2. Login to get token
        String token = restClient.post()
                .uri("/client/api/v1/auth/login")
                .body(credentials)
                .retrieve()
                .body(String.class);

        assertNotNull(token);
        assertTrue(token.length() > 10);

        // 3. Get Client ID via /me
        JsonNode meResponse = restClient.get()
                .uri("/client/api/v1/clients/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);

        assertNotNull(meResponse);
        long clientId = meResponse.get("id").asLong();

        // 4. Add Balance
        restClient.patch()
                .uri("/client/api/v1/clients/" + clientId + "/balance?amount=5000.0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();

        // 5. Add Product
        Map<String, Object> newProduct = Map.of(
                "name", "Integration Test Product",
                "description", "System Test",
                "price", 1000.00,
                "category", "ELECTRONICS"
        );

        String response = restClient.post()
                .uri("/product/api/v1/products")
                .body(newProduct)
                .retrieve()
                .body(String.class);

        assertNotNull(response);
        Long productId = Long.parseLong(response);

        // 6. Add Discount
        List<Map<String, Object>> discounts = List.of(
                Map.of("productId", productId, "discount", 0.20)
        );
        
        restClient.post()
                .uri("/discount/api/v1/discounts")
                .body(discounts)
                .retrieve()
                .toBodilessEntity();

        // 7. Purchase with Orchestrator (SAGA)
        String orderId = java.util.UUID.randomUUID().toString();
        Map<String, Object> orderDto = Map.of(
                "orderId", orderId,
                "clientID", clientId,
                "items", List.of(
                        Map.of("id", productId, "name", "Integration Test Product", "quantity", 1, "price", 800.00) // 1000 - 20%
                ),
                "totalSum", 800.00
        );

        Boolean purchaseResult = restClient.post()
                .uri("/orchestrator/api/v1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(orderDto)
                .retrieve()
                .body(Boolean.class);

        assertNotNull(purchaseResult);
        assertTrue(purchaseResult, "SAGA purchase should be successful");
        
        // Verify balance was deducted
        JsonNode updatedMe = restClient.get()
                .uri("/client/api/v1/clients/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);
                
        double finalBalance = updatedMe.get("balance").asDouble();
        assertEquals(4200.00, finalBalance, 0.1);
    }
}
