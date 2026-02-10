package com.jobmonitor.dbperf.service;

import com.jobmonitor.dbperf.dto.ExplainPlanResult;
import com.jobmonitor.dbperf.model.MonitoredDatabase;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ExplainAnalyzeService Unit Tests")
class ExplainAnalyzeServiceTest {

    private ExplainAnalyzeService service;

    @BeforeEach
    void setUp() {
        service = new ExplainAnalyzeService();
    }

    @Nested
    @DisplayName("suggestRewrites()")
    class RewriteSuggestionTests {

        @Test
        @DisplayName("should detect SELECT * anti-pattern")
        void shouldDetectSelectStar() {
            List<String> suggestions = service.suggestRewrites("SELECT * FROM users WHERE id = 1");

            assertThat(suggestions).anyMatch(s -> s.contains("SELECT *"));
        }

        @Test
        @DisplayName("should detect NOT IN anti-pattern")
        void shouldDetectNotIn() {
            List<String> suggestions = service.suggestRewrites(
                    "SELECT id FROM orders WHERE user_id NOT IN (SELECT id FROM blocked_users)");

            assertThat(suggestions).anyMatch(s -> s.contains("NOT IN"));
        }

        @Test
        @DisplayName("should detect leading wildcard LIKE")
        void shouldDetectLeadingWildcardLike() {
            List<String> suggestions = service.suggestRewrites(
                    "SELECT * FROM products WHERE name LIKE '%widget%'");

            assertThat(suggestions).anyMatch(s -> s.contains("wildcard"));
        }

        @Test
        @DisplayName("should detect missing LIMIT with ORDER BY")
        void shouldDetectMissingLimit() {
            List<String> suggestions = service.suggestRewrites(
                    "SELECT id, name FROM users ORDER BY created_at DESC");

            assertThat(suggestions).anyMatch(s -> s.contains("LIMIT"));
        }

        @Test
        @DisplayName("should detect DISTINCT usage")
        void shouldDetectDistinct() {
            List<String> suggestions = service.suggestRewrites(
                    "SELECT DISTINCT email FROM users JOIN orders ON users.id = orders.user_id");

            assertThat(suggestions).anyMatch(s -> s.contains("DISTINCT"));
        }

        @Test
        @DisplayName("should return empty for clean query")
        void shouldReturnEmptyForCleanQuery() {
            List<String> suggestions = service.suggestRewrites(
                    "SELECT id, name FROM users WHERE id = 1 LIMIT 10");

            assertThat(suggestions).isEmpty();
        }

        @Test
        @DisplayName("should detect OR conditions")
        void shouldDetectOrConditions() {
            List<String> suggestions = service.suggestRewrites(
                    "SELECT id FROM users WHERE name = 'john' OR email = 'john@test.com'");

            assertThat(suggestions).anyMatch(s -> s.contains("OR"));
        }
    }

    @Nested
    @DisplayName("estimateCost()")
    class EstimateCostTests {

        @Test
        @DisplayName("should return failure for unreachable database")
        void shouldReturnFailureForUnreachableDb() {
            MonitoredDatabase db = MonitoredDatabase.builder()
                    .name("test-db")
                    .jdbcUrl("jdbc:postgresql://nonexistent:5432/test")
                    .username("user")
                    .encryptedPassword("pass")
                    .build();

            ExplainPlanResult result = service.estimateCost(db, "SELECT 1");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isNotNull();
        }
    }
}
