package com.jobmonitor.platform.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Centralized async configuration — all thread pool sizes come from
 * {@link PlatformProperties.Async} (externalized YAML, zero hardcoding).
 * <p>
 * Provides named executors for different workloads:
 * <ul>
 *   <li>{@code taskExecutor} — general async tasks</li>
 *   <li>{@code notificationExecutor} — notification dispatch</li>
 *   <li>{@code jobQueueExecutor} — background job processing</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final PlatformProperties properties;

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        return buildExecutor(properties.getAsync().getTaskPool(), "taskExecutor");
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        return buildExecutor(properties.getAsync().getNotificationPool(), "notificationExecutor");
    }

    @Bean(name = "jobQueueExecutor")
    public Executor jobQueueExecutor() {
        return buildExecutor(properties.getAsync().getJobQueuePool(), "jobQueueExecutor");
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            log.error("Async exception in method={}, class={}: {}",
                    method.getName(),
                    method.getDeclaringClass().getSimpleName(),
                    ex.getMessage(),
                    ex);
        };
    }

    private ThreadPoolTaskExecutor buildExecutor(PlatformProperties.Async.Pool pool, String beanName) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool.getCoreSize());
        executor.setMaxPoolSize(pool.getMaxSize());
        executor.setQueueCapacity(pool.getQueueCapacity());
        executor.setThreadNamePrefix(pool.getThreadPrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(pool.getAwaitTerminationSeconds());
        executor.setRejectedExecutionHandler((runnable, poolExecutor) ->
                log.warn("Task rejected from {} — pool exhausted (active={}, queue={})",
                        beanName,
                        poolExecutor.getActiveCount(),
                        poolExecutor.getQueue().size()));
        executor.initialize();
        log.info("Initialized executor '{}': core={}, max={}, queue={}",
                beanName, pool.getCoreSize(), pool.getMaxSize(), pool.getQueueCapacity());
        return executor;
    }
}
