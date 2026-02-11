package com.jobmonitor.monitoring.integration;

import com.jobmonitor.platform.common.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying Redis operations against a real Redis container.
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void setAndGet_stringsWork() {
        redisTemplate.opsForValue().set("test-key", "test-value", Duration.ofMinutes(1));
        String result = redisTemplate.opsForValue().get("test-key");
        assertThat(result).isEqualTo("test-value");
    }

    @Test
    void ttl_keysExpire() throws InterruptedException {
        redisTemplate.opsForValue().set("expiring-key", "temp", Duration.ofMillis(200));
        assertThat(redisTemplate.hasKey("expiring-key")).isTrue();

        Thread.sleep(300);
        assertThat(redisTemplate.hasKey("expiring-key")).isFalse();
    }

    @Test
    void hashOperations_work() {
        redisTemplate.opsForHash().put("job-cache", "field1", "value1");
        redisTemplate.opsForHash().put("job-cache", "field2", "value2");

        Object result = redisTemplate.opsForHash().get("job-cache", "field1");
        assertThat(result).isEqualTo("value1");

        Long size = redisTemplate.opsForHash().size("job-cache");
        assertThat(size).isEqualTo(2);
    }

    @Test
    void deleteKey_works() {
        redisTemplate.opsForValue().set("to-delete", "bye");
        assertThat(redisTemplate.hasKey("to-delete")).isTrue();

        redisTemplate.delete("to-delete");
        assertThat(redisTemplate.hasKey("to-delete")).isFalse();
    }
}
