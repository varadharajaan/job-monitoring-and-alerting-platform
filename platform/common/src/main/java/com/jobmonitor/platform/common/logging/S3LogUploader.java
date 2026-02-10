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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Uploads rotated JSONL log files to S3 for Athena queries.
 * Uses Optional, Predicate, Consumer, and Stream for functional-style I/O.
 *
 * @see PlatformProperties.S3Config
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3LogUploader {

    private static final String GZIP_CONTENT_TYPE = "application/gzip";
    private static final String JSONL_GZ_SUFFIX = ".jsonl.gz";
    private static final Predicate<Path> IS_JSONL_GZ = path ->
            path.getFileName().toString().endsWith(JSONL_GZ_SUFFIX);

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

        Optional.of(archiveDir)
                .filter(Files::isDirectory)
                .ifPresentOrElse(
                        dir -> uploadAllLogs(dir, s3Config),
                        () -> log.debug("No archive directory found at {} — skipping", archiveDir)
                );
    }

    private void uploadAllLogs(Path archiveDir, PlatformProperties.S3Config s3Config) {
        var today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        var serviceName = resolveServiceName();

        Consumer<Path> uploadAndDelete = file -> {
            var key = String.format("%s/%s/%s/%s",
                    s3Config.getPathPrefix(), serviceName, today,
                    file.getFileName().toString());

            var request = PutObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(key)
                    .contentType(GZIP_CONTENT_TYPE)
                    .build();

            s3Client.putObject(request, RequestBody.fromFile(file));
            log.info("Uploaded log to s3://{}/{}", s3Config.getBucket(), key);

            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("Failed to delete uploaded log file: {}", file, e);
            }
        };

        try (Stream<Path> files = Files.list(archiveDir)) {
            files.filter(IS_JSONL_GZ)
                 .forEach(uploadAndDelete);
        } catch (IOException e) {
            log.error("Failed to upload rotated logs to S3", e);
        }
    }

    private String resolveServiceName() {
        return Optional.ofNullable(System.getProperty("spring.application.name"))
                .filter(name -> !name.isBlank())
                .orElse("unknown-service");
    }
}
