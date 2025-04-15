package com.example.retail.integration;

import org.junit.jupiter.api.*; // Import BeforeEach
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header; // Import Header
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
// Import Times for verification count
import static org.mockserver.verify.VerificationTimes.exactly;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
// Import json helper for matching bodies
import static org.mockserver.model.JsonBody.json;

class OrderServiceMockServerIT {

    private static ClientAndServer mockServer; // Use ClientAndServer
    private static RestTemplate restTemplate;
    private static final int MOCK_SERVER_PORT = 1080; // Define port

    @BeforeAll
    static void startServer() {
        // Start the MockServer instance
        mockServer = ClientAndServer.startClientAndServer(MOCK_SERVER_PORT);
        restTemplate = new RestTemplate();
    }

    @AfterAll
    static void stopServer() {
        if (mockServer != null && mockServer.isRunning()) { // Check if running before stopping
            mockServer.stop();
        }
    }

    @BeforeEach // Add BeforeEach for resetting state
    void resetMockServer() {
        if (mockServer != null) {
            // Reset expectations and request log before each test
            mockServer.reset();
        }
    }

    @Test
    void shouldReturnOrderDetails() {
        // Arrange: Setup expectation (stub) using the mockServer instance
        mockServer.when(
                HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/orders/123")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(json("""
                                {
                                  "orderId": "123",
                                  "product": "Laptop",
                                  "quantity": 1
                                }
                                """)) // Use json() helper
        );

        String url = "http://localhost:" + MOCK_SERVER_PORT + "/orders/123";
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Laptop"));

        // Verify
        mockServer.verify(
                HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/orders/123"),
                exactly(1) // Verify exactly once
        );
    }

    @Test
    void shouldReturn404WhenOrderNotFound() {
        // Arrange: Setup expectation (stub)
        mockServer.when(
                HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/orders/9999")
        ).respond(
                HttpResponse.response().withStatusCode(404)
        );

        String url = "http://localhost:" + MOCK_SERVER_PORT + "/orders/9999";

        // Act & Assert
        try {
            restTemplate.getForEntity(url, String.class);
            fail("Expected HttpClientErrorException.NotFound");
        } catch (HttpClientErrorException.NotFound ex) {
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());

            // Verify
            mockServer.verify(
                    HttpRequest.request()
                            .withMethod("GET")
                            .withPath("/orders/9999"),
                    exactly(1)
            );
        }
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        // Arrange: Define request body
        String requestBodyJson = """
                {
                  "productId": "123",
                  "quantity": 2
                }
                """;
        // Arrange: Setup expectation (stub)
        mockServer.when(
                HttpRequest.request()
                        .withMethod("POST")
                        .withPath("/orders")
                        // Use json() helper for request body matching
                        .withBody(json(requestBodyJson))
                        .withHeader("Content-Type", "application/json.*") // Flexible header match
        ).respond(
                HttpResponse.response()
                        .withStatusCode(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(json("""
                                {
                                  "orderId": "abc-001",
                                  "status": "CREATED"
                                }
                                """)) // Use json() helper for response
        );

        String url = "http://localhost:" + MOCK_SERVER_PORT + "/orders";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBodyJson, headers); // Use defined JSON string

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("abc-001"));

        // Verify
        mockServer.verify(
                HttpRequest.request()
                        .withMethod("POST")
                        .withPath("/orders")
                        // Verify body using json() matcher
                        .withBody(json(requestBodyJson))
                        // Verify header precisely
                        .withHeader(new Header("Content-Type", MediaType.APPLICATION_JSON_VALUE)),
                exactly(1) // Verify exactly once
        );
    }
}