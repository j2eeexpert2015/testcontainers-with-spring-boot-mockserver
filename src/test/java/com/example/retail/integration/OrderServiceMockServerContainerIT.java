package com.example.retail.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Header; // Import Header for verification
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
// Import Times for verification count
import static org.mockserver.verify.VerificationTimes.exactly;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Import MockServer specific utilities, like the static docker image constant if desired
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;
// Import json helper for matching bodies
import static org.mockserver.model.JsonBody.json;

@Testcontainers
public class OrderServiceMockServerContainerIT {

    // Define the image name consistently
    private static final DockerImageName MOCKSERVER_IMAGE = DockerImageName
            .parse("mockserver/mockserver")
            .withTag("mockserver-" + MockServerClient.class.getPackage().getImplementationVersion());

    @Container
    // Use the defined image name
    static MockServerContainer mockServerContainer = new MockServerContainer(MOCKSERVER_IMAGE);

    private static final RestTemplate restTemplate = new RestTemplate();
    private MockServerClient mockServerClient;

    @BeforeEach
    void setup() {
        // Initialize client pointing to the container
        mockServerClient = new MockServerClient(mockServerContainer.getHost(), mockServerContainer.getServerPort());
        // Reset expectations and recorded requests before each test
        mockServerClient.reset();
    }

    @Test
    void shouldReturnOrderDetails() {
        // Arrange: Setup expectation (stub)
        mockServerClient
                .when(
                        HttpRequest.request()
                                .withMethod("GET")
                                .withPath("/orders/123")
                )
                .respond(
                        HttpResponse.response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(json("""
                                {
                                  "orderId": "123",
                                  "product": "Laptop",
                                  "quantity": 1
                                }
                                """)) // Use json() helper for response body too
                );

        String url = mockServerContainer.getEndpoint() + "/orders/123";

        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Laptop"));

        // *** ADD VERIFICATION ***
        mockServerClient.verify(
                HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/orders/123"),
                exactly(1) // Verify it was called exactly once
        );
    }

    @Test
    void shouldReturn404WhenOrderNotFound() {
        // Arrange: Setup expectation (stub)
        mockServerClient
                .when(
                        HttpRequest.request()
                                .withMethod("GET")
                                .withPath("/orders/9999")
                )
                .respond(HttpResponse.response().withStatusCode(404));

        String url = mockServerContainer.getEndpoint() + "/orders/9999";

        // Act & Assert
        try {
            restTemplate.getForEntity(url, String.class);
            fail("Expected HttpClientErrorException.NotFound");
        } catch (HttpClientErrorException.NotFound ex) {
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());

            // Verify the request was still made
            mockServerClient.verify(
                    HttpRequest.request()
                            .withMethod("GET")
                            .withPath("/orders/9999"),
                    exactly(1)
            );
        }
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        // Arrange: Define request and response bodies
        String requestBodyJson = """
                {
                  "productId": "123",
                  "quantity": 2
                }
                """;
        String responseBodyJson = """
                {
                  "orderId": "abc-001",
                  "status": "CREATED"
                }
                """;

        // Arrange: Setup expectation (stub) matching the exact JSON body
        mockServerClient
                .when(
                        HttpRequest.request()
                                .withMethod("POST")
                                .withPath("/orders")
                                // Use json() matcher for precise body matching in expectation
                                .withBody(json(requestBodyJson))
                                .withHeader("Content-Type", "application/json.*") // Match header flexibly
                )
                .respond(
                        HttpResponse.response()
                                .withStatusCode(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody(json(responseBodyJson)) // Use json() helper here too
                );

        String url = mockServerContainer.getEndpoint() + "/orders";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBodyJson, headers); // Use the defined JSON string

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("abc-001"));

        // Verify the POST request was made with the correct path, body, and header
        mockServerClient.verify(
                HttpRequest.request()
                        .withMethod("POST")
                        .withPath("/orders")
                        // Use json() matcher for precise body verification
                        .withBody(json(requestBodyJson))
                        // Verify header was present and correct
                        .withHeader(new Header("Content-Type", MediaType.APPLICATION_JSON_VALUE)),
                exactly(1) // Verify exactly one matching request
        );
    }
}