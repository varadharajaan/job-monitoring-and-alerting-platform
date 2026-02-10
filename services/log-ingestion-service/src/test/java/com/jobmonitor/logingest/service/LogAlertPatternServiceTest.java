package com.jobmonitor.logingest.service;

import com.jobmonitor.logingest.model.LogAlertPattern;
import com.jobmonitor.logingest.model.LogEntry;
import com.jobmonitor.logingest.repository.LogAlertPatternRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogAlertPatternService Unit Tests")
class LogAlertPatternServiceTest {

    @Mock private LogAlertPatternRepository repository;
    private LogAlertPatternService service;

    @BeforeEach
    void setUp() {
        service = new LogAlertPatternService(repository, new SimpleMeterRegistry());
    }

    @Nested
    @DisplayName("createPattern()")
    class CreatePatternTests {

        @Test
        @DisplayName("should save valid pattern")
        void shouldSaveValidPattern() {
            var pattern = LogAlertPattern.builder()
                    .tenantId("t1")
                    .name("OOM Detection")
                    .regexPattern("OutOfMemoryError|OOM")
                    .minLevel(LogEntry.LogLevel.ERROR)
                    .build();
            when(repository.save(any())).thenReturn(pattern);

            var result = service.createPattern(pattern);

            assertThat(result).isNotNull();
            verify(repository).save(pattern);
        }

        @Test
        @DisplayName("should reject invalid regex")
        void shouldRejectInvalidRegex() {
            var pattern = LogAlertPattern.builder()
                    .tenantId("t1")
                    .name("Bad Regex")
                    .regexPattern("[invalid(")
                    .minLevel(LogEntry.LogLevel.ERROR)
                    .build();

            assertThatThrownBy(() -> service.createPattern(pattern))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid regex");
        }
    }

    @Nested
    @DisplayName("evaluateEntry()")
    class EvaluateTests {

        @Test
        @DisplayName("should match log entry against pattern")
        void shouldMatchLogEntry() {
            var pattern = LogAlertPattern.builder()
                    .id(UUID.randomUUID())
                    .tenantId("t1")
                    .name("Exception Alert")
                    .regexPattern("NullPointerException|IOException")
                    .minLevel(LogEntry.LogLevel.ERROR)
                    .enabled(true)
                    .build();
            when(repository.findByTenantIdAndEnabledTrue("t1")).thenReturn(List.of(pattern));

            var entry = LogEntry.builder()
                    .id("log-1")
                    .tenantId("t1")
                    .level(LogEntry.LogLevel.ERROR)
                    .message("java.lang.NullPointerException at com.example.App.run")
                    .timestamp(Instant.now())
                    .build();

            boolean result = service.evaluateEntry(entry);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should not match if log level too low")
        void shouldNotMatchLowLevel() {
            var pattern = LogAlertPattern.builder()
                    .id(UUID.randomUUID())
                    .tenantId("t1")
                    .name("Error Only")
                    .regexPattern(".*")
                    .minLevel(LogEntry.LogLevel.ERROR)
                    .enabled(true)
                    .build();
            when(repository.findByTenantIdAndEnabledTrue("t1")).thenReturn(List.of(pattern));

            var entry = LogEntry.builder()
                    .id("log-2")
                    .tenantId("t1")
                    .level(LogEntry.LogLevel.INFO)
                    .message("Application started")
                    .timestamp(Instant.now())
                    .build();

            boolean result = service.evaluateEntry(entry);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should filter by service when service filter is set")
        void shouldFilterByService() {
            var pattern = LogAlertPattern.builder()
                    .id(UUID.randomUUID())
                    .tenantId("t1")
                    .name("Payment Service Errors")
                    .regexPattern(".*")
                    .minLevel(LogEntry.LogLevel.ERROR)
                    .serviceFilter("payment-service")
                    .enabled(true)
                    .build();
            when(repository.findByTenantIdAndEnabledTrue("t1")).thenReturn(List.of(pattern));

            var entry = LogEntry.builder()
                    .id("log-3")
                    .tenantId("t1")
                    .level(LogEntry.LogLevel.ERROR)
                    .service("order-service")
                    .message("Connection refused")
                    .timestamp(Instant.now())
                    .build();

            boolean result = service.evaluateEntry(entry);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("updatePattern()")
    class UpdateTests {

        @Test
        @DisplayName("should update existing pattern fields")
        void shouldUpdateExisting() {
            var id = UUID.randomUUID();
            var existing = LogAlertPattern.builder()
                    .id(id)
                    .tenantId("t1")
                    .name("Old Name")
                    .regexPattern("old")
                    .minLevel(LogEntry.LogLevel.WARN)
                    .enabled(true)
                    .build();
            when(repository.findById(id)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            var update = LogAlertPattern.builder()
                    .name("New Name")
                    .regexPattern("new-pattern")
                    .minLevel(LogEntry.LogLevel.ERROR)
                    .enabled(false)
                    .build();

            var result = service.updatePattern(id, update);

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getRegexPattern()).isEqualTo("new-pattern");
            assertThat(result.getMinLevel()).isEqualTo(LogEntry.LogLevel.ERROR);
            assertThat(result.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should throw when pattern not found")
        void shouldThrowWhenNotFound() {
            var id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePattern(id, LogAlertPattern.builder().build()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
