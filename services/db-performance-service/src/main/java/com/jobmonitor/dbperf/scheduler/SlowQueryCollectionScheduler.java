package com.jobmonitor.dbperf.scheduler;

import com.jobmonitor.dbperf.model.MonitoredDatabase;
import com.jobmonitor.dbperf.service.MonitoredDatabaseService;
import com.jobmonitor.dbperf.service.SlowQueryMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically collects slow queries from all enabled monitored databases.
 * Runs every 5 minutes.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SlowQueryCollectionScheduler {

    private final MonitoredDatabaseService monitoredDatabaseService;
    private final SlowQueryMonitorService slowQueryMonitorService;

    @Scheduled(fixedRateString = "${app.scheduler.slow-query-interval-ms:300000}")
    public void collectSlowQueries() {
        log.info("Starting scheduled slow query collection...");
        List<MonitoredDatabase> databases = monitoredDatabaseService.getAllEnabled();

        int totalCollected = 0;
        for (MonitoredDatabase db : databases) {
            try {
                int collected = slowQueryMonitorService.collectSlowQueries(db);
                totalCollected += collected;
            } catch (Exception e) {
                log.error("Failed to collect slow queries from database={}: {}",
                        db.getName(), e.getMessage(), e);
            }
        }
        log.info("Slow query collection complete: {} databases scanned, {} new queries captured",
                databases.size(), totalCollected);
    }
}
