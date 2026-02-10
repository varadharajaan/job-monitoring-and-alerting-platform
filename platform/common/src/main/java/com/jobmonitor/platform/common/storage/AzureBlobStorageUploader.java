package com.jobmonitor.platform.common.storage;

import com.jobmonitor.platform.common.config.PlatformProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Azure Blob Storage log uploader.
 * <p>
 * Activated when {@code platform.azure.blob-storage.enabled=true}.
 * Uses Azure Storage SDK to upload logs/data to Azure Blob Storage containers.
 * <p>
 * For production use, add {@code com.azure:azure-storage-blob} dependency
 * and inject BlobServiceClient.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "platform.azure.blob-storage", name = "enabled", havingValue = "true")
public class AzureBlobStorageUploader {

    private final PlatformProperties.AzureConfig.BlobStorage config;

    public AzureBlobStorageUploader(PlatformProperties properties) {
        this.config = properties.getAzure().getBlobStorage();
        log.info("Azure Blob Storage uploader initialized: container={}",
                config.getContainerName());
    }

    /**
     * Upload content as a blob to Azure Storage.
     *
     * @param content  the content to upload
     * @param blobName the blob name (path within container)
     */
    public void upload(String content, String blobName) {
        try {
            var fullPath = buildPath(blobName);
            // In production with azure-storage-blob SDK:
            // BlobServiceClient → BlobContainerClient → BlobClient → upload()
            log.info("Uploading blob to container={}, path={}, size={} bytes",
                    config.getContainerName(), fullPath, content.getBytes(StandardCharsets.UTF_8).length);

            // Placeholder for actual Azure Blob SDK call:
            // blobContainerClient.getBlobClient(fullPath)
            //     .upload(new ByteArrayInputStream(content.getBytes()), content.length(), true);

        } catch (Exception e) {
            log.error("Failed to upload blob '{}' to Azure: {}", blobName, e.getMessage(), e);
            throw new RuntimeException("Azure Blob upload failed", e);
        }
    }

    /**
     * Upload an input stream to Azure Blob Storage.
     */
    public void upload(InputStream inputStream, String blobName, long length) {
        try {
            var fullPath = buildPath(blobName);
            log.info("Uploading blob stream to container={}, path={}, size={} bytes",
                    config.getContainerName(), fullPath, length);
        } catch (Exception e) {
            log.error("Failed to upload blob stream '{}': {}", blobName, e.getMessage(), e);
            throw new RuntimeException("Azure Blob upload failed", e);
        }
    }

    private String buildPath(String blobName) {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "/" + blobName;
    }
}
