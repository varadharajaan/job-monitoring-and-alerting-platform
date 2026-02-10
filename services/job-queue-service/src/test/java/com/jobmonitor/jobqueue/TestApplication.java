package com.jobmonitor.jobqueue;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot configuration for @WebMvcTest tests in this package.
 * Needed because the main application class is in com.jobmonitor.queue,
 * which is not an ancestor package of com.jobmonitor.jobqueue.
 */
@SpringBootApplication
public class TestApplication {
}
