package com.jobmonitor.platform.common.logging;

import com.jobmonitor.platform.common.config.PlatformProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Uploads rotated JSONL log files to S3 for Athena queries.
 * <p>
 * Runs on a configurable cron schedule from {@code platform.s3.log-upload-cron}.
 * S3 path layout: {@code s3://{bucket}/{prefix}/{service}/{date}/{filename}}
 * — Athena-friendly partitioning by service and date.
 *
 * @see PlatformProperties.S3Config
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3LogUploader {

    private final PlatformProperties properties;
    private final S3Client s3Client;

    @Scheduled(cron = "${platform.s3.log-upload-cron}")
    public void uploadRotatedLogs() {
        var s3Config = properties.getS3();
        if (!s3Config.isEnabled()) {
            log.debug("S3 log upload disabled — skipping");
            return;
        }

        var archiveDir = Path.of(properties.getLogging().getDir(), "archive");
        if (!Files.isDirectory(archiveDir)) {
            log.debug("No archive directory found at {} — skipping", archiveDir);
            return;
        }

        var today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        var serviceName = resolveServiceName();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(archiveDir, "*.jsonl.gz")) {
            for (var file : stream) {
                var key = String.format("%s/%s/%s/%s",
                        s3Config.getPathPrefix(),
                        serviceName,
                        today,
                        file.getFileName().toString()
                );

                var request = PutObjectRequest.builder()
                        .bucket(s3Config.getBucket())
                        .key(key)
                        .contentType("application/gzip")
                        .build();

                s3Client.putObject(request, RequestBody.fromFile(file));
                log.info("Uploaded log to s3://{}/{}", s3Config.getBucket(), key);

                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            log.error("Failed to upload rotated logs to S3", e);
        }
    }

    private String resolveServiceName() {
        return System.getProperty("spring.application.name", "unknown-service");
    }
}
