package com.jobmonitor.logingest.service;

import com.jobmonitor.logingest.model.LogAlertPattern;
import com.jobmonitor.logingest.model.LogEntry;
import com.jobmonitor.logingest.repository.LogAlertPatternRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Manages log alert patterns and evaluates incoming log entries against them.
 * When a log entry matches a pattern (regex + level + optional service filter),
 * an alert is triggered via the notification topic.
 */
@Service
@Slf4j
public class LogAlertPatternService {

    private final LogAlertPatternRepository repository;
    private final Counter alertTriggeredCounter;

    public LogAlertPatternService(LogAlertPatternRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.alertTriggeredCounter = Counter.builder("log.alert.triggered")
                .description("Number of log pattern alerts triggered")
                .register(meterRegistry);
    }

    public LogAlertPattern createPattern(LogAlertPattern pattern) {
        validateRegex(pattern.getRegexPattern());
        return repository.save(pattern);
    }

    public List<LogAlertPattern> getPatternsForTenant(String tenantId) {
        return repository.findByTenantIdAndEnabledTrue(tenantId);
    }

    public Optional<LogAlertPattern> getById(UUID id) {
        return repository.findById(id);
    }

    public void deletePattern(UUID id) {
        repository.deleteById(id);
    }

    public LogAlertPattern updatePattern(UUID id, LogAlertPattern updated) {
        return repository.findById(id)
                .map(existing -> {
                    if (updated.getName() != null) existing.setName(updated.getName());
                    if (updated.getRegexPattern() != null) {
                        validateRegex(updated.getRegexPattern());
                        existing.setRegexPattern(updated.getRegexPattern());
                    }
                    if (updated.getMinLevel() != null) existing.setMinLevel(updated.getMinLevel());
                    if (updated.getServiceFilter() != null) existing.setServiceFilter(updated.getServiceFilter());
                    if (updated.getNotificationChannel() != null) existing.setNotificationChannel(updated.getNotificationChannel());
                    if (updated.getNotificationRecipient() != null) existing.setNotificationRecipient(updated.getNotificationRecipient());
                    existing.setEnabled(updated.isEnabled());
                    return repository.save(existing);
                })
                .orElseThrow(() -> new IllegalArgumentException("Pattern not found: " + id));
    }

    /**
     * Evaluates a log entry against all enabled patterns for its tenant.
     * Returns true if at least one pattern matched.
     */
    @Timed(value = "log.alert.evaluate", description = "Log alert pattern evaluation time")
    public boolean evaluateEntry(LogEntry entry) {
        List<LogAlertPattern> patterns = repository.findByTenantIdAndEnabledTrue(entry.getTenantId());
        boolean anyMatch = false;

        for (LogAlertPattern pattern : patterns) {
            if (matches(entry, pattern)) {
                anyMatch = true;
                alertTriggeredCounter.increment();
                log.warn("Log alert pattern matched: pattern={}, logId={}, service={}, message={}",
                        pattern.getName(), entry.getId(), entry.getService(),
                        truncate(entry.getMessage(), 200));
            }
        }
        return anyMatch;
    }

    private boolean matches(LogEntry entry, LogAlertPattern pattern) {
        // Level check
        if (entry.getLevel() == null || entry.getLevel().ordinal() < pattern.getMinLevel().ordinal()) {
            return false;
        }
        // Service filter check
        if (pattern.getServiceFilter() != null && !pattern.getServiceFilter().isBlank()) {
            if (!pattern.getServiceFilter().equals(entry.getService())) {
                return false;
            }
        }
        // Regex pattern match
        try {
            return Pattern.compile(pattern.getRegexPattern(), Pattern.CASE_INSENSITIVE)
                    .matcher(Optional.ofNullable(entry.getMessage()).orElse(""))
                    .find();
        } catch (PatternSyntaxException e) {
            log.error("Invalid regex in pattern {}: {}", pattern.getId(), e.getMessage());
            return false;
        }
    }

    private void validateRegex(String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage());
        }
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
