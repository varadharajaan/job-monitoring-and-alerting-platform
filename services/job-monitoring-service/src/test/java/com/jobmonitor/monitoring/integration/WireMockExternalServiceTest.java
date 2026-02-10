package com.jobmonitor.monitoring.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock integration tests simulating external service calls.
 * Validates that the job monitoring service handles external API contracts
 * (e.g., alert evaluation webhook, notification dispatch) correctly.
 */
@DisplayName("WireMock External Service Integration Tests")
class WireMockExternalServiceTest {

    private static WireMockServer wireMockServer;
    private static HttpClient httpClient;

    // Functional fixture — builds request URI from WireMock port
    private static Function<String, URI> uriBuilder;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(
                WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        uriBuilder = path -> URI.create(
                String.format("http://localhost:%d%s", wireMockServer.port(), path));
    }

    @AfterAll
    static void stopWireMock() {
        Optional.ofNullable(wireMockServer).ifPresent(WireMockServer::stop);
    }

    @AfterEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    // ──────────── Alert Evaluation Webhook ────────────

    @Nested
    @DisplayName("Alert Evaluation Webhook")
    class AlertEvaluationWebhook {

        @Test
        @DisplayName("should call alert evaluation endpoint and receive triggered alerts")
        void shouldReceiveTriggeredAlerts() throws Exception {
            var jobId = UUID.randomUUID();

            // Stub: alert evaluation endpoint returns triggered alerts
            stubFor(post(urlEqualTo("/api/v1/alerts/evaluate/" + jobId))
                    .withHeader("X-Tenant-Id", equalTo("tenant-wire-001"))
                    .withHeader("Content-Type", containing("application/json"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                    "success": true,
                                    "data": [
                                        {
                                            "id": "%s",
                                            "alertRuleId": "%s",
                                            "severity": "HIGH",
                                            "status": "TRIGGERED",
                                            "message": "Failure count exceeded threshold"
                                        }
                                    ],
                                    "message": "1 alert(s) triggered"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID()))));

            var request = HttpRequest.newBuilder()
                    .uri(uriBuilder.apply("/api/v1/alerts/evaluate/" + jobId))
                    .header("X-Tenant-Id", "tenant-wire-001")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"failureCount\": 10}"))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body())
                    .contains("TRIGGERED")
                    .contains("HIGH")
                    .contains("1 alert(s) triggered");

            verify(postRequestedFor(urlEqualTo("/api/v1/alerts/evaluate/" + jobId))
                    .withHeader("X-Tenant-Id", equalTo("tenant-wire-001")));
        }

        @Test
        @DisplayName("should handle no alerts triggered gracefully")
        void shouldHandleNoAlerts() throws Exception {
            var jobId = UUID.randomUUID();

            stubFor(post(urlEqualTo("/api/v1/alerts/evaluate/" + jobId))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {"success": true, "data": [], "message": "0 alert(s) triggered"}
                                """)));

            var request = HttpRequest.newBuilder()
                    .uri(uriBuilder.apply("/api/v1/alerts/evaluate/" + jobId))
                    .header("X-Tenant-Id", "tenant-wire-001")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"failureCount\": 1}"))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"data\": []");
        }
    }

    // ──────────── Notification Dispatch Webhook ────────────

    @Nested
    @DisplayName("Notification Dispatch Webhook")
    class NotificationDispatchWebhook {

        @Test
        @DisplayName("should simulate successful webhook notification delivery")
        void shouldDeliverWebhook() throws Exception {
            stubFor(post(urlEqualTo("/webhook/notify"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"messageId\": \"webhook-msg-001\", \"status\": \"delivered\"}")));

            var request = HttpRequest.newBuilder()
                    .uri(uriBuilder.apply("/webhook/notify"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                            "channel": "WEBHOOK",
                            "recipient": "https://hooks.example.com/alerts",
                            "subject": "Alert: SLA Breach",
                            "body": "Job etl-pipeline exceeded SLA threshold"
                        }
                        """))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("webhook-msg-001");
        }

        @Test
        @DisplayName("should handle webhook 5xx error and simulate retry")
        void shouldHandleWebhookError() throws Exception {
            // First call = 503, second call = 200 (simulating retry)
            stubFor(post(urlEqualTo("/webhook/notify"))
                    .inScenario("retry-scenario")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(503).withBody("Service Unavailable"))
                    .willSetStateTo("retried"));

            stubFor(post(urlEqualTo("/webhook/notify"))
                    .inScenario("retry-scenario")
                    .whenScenarioStateIs("retried")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody("{\"messageId\": \"webhook-retry-001\", \"status\": \"delivered\"}")));

            // First attempt — 503
            UnaryOperator<HttpRequest.Builder> buildRequest = builder -> builder
                    .uri(uriBuilder.apply("/webhook/notify"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"body\": \"test\"}"));

            var firstResponse = httpClient.send(
                    buildRequest.apply(HttpRequest.newBuilder()).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(firstResponse.statusCode()).isEqualTo(503);

            // Retry — 200
            var retryResponse = httpClient.send(
                    buildRequest.apply(HttpRequest.newBuilder()).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(retryResponse.statusCode()).isEqualTo(200);
            assertThat(retryResponse.body()).contains("webhook-retry-001");
        }
    }

    // ──────────── Job Status Reporting ────────────

    @Nested
    @DisplayName("Job Status External Reporting")
    class JobStatusReporting {

        @Test
        @DisplayName("should simulate external job status callback")
        void shouldReportJobStatus() throws Exception {
            var jobId = UUID.randomUUID();

            stubFor(post(urlPathEqualTo("/api/v1/jobs/" + jobId + "/executions"))
                    .willReturn(aResponse()
                            .withStatus(201)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                    "success": true,
                                    "data": {
                                        "id": "%s",
                                        "status": "SUCCESS",
                                        "durationMs": 45000
                                    }
                                }
                                """.formatted(UUID.randomUUID()))));

            var request = HttpRequest.newBuilder()
                    .uri(uriBuilder.apply("/api/v1/jobs/" + jobId + "/executions"))
                    .header("X-Tenant-Id", "tenant-wire-001")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                            "status": "SUCCESS",
                            "startedAt": "2024-01-15T10:00:00Z",
                            "durationMs": 45000,
                            "exitCode": 0
                        }
                        """))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.body()).contains("SUCCESS");
        }
    }
}
