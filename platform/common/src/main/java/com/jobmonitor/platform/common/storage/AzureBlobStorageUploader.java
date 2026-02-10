package com.jobmonitor.platform.common.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
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
 * Uses the Azure Storage Blob SDK to upload logs/data to Azure Blob Storage containers.
 * <p>
 * Requires {@code com.azure:azure-storage-blob} on the classpath and
 * {@code platform.azure.blob-storage.connection-string} to be configured.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "platform.azure.blob-storage", name = "enabled", havingValue = "true")
public class AzureBlobStorageUploader {

    private final PlatformProperties.AzureConfig.BlobStorage config;
    private final BlobContainerClient containerClient;

    public AzureBlobStorageUploader(PlatformProperties properties) {
        this.config = properties.getAzure().getBlobStorage();

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(config.getConnectionString())
                .buildClient();

        this.containerClient = blobServiceClient.getBlobContainerClient(config.getContainerName());

        // Create the container if it does not exist
        if (!containerClient.exists()) {
            containerClient.create();
            log.info("Created Azure Blob container: {}", config.getContainerName());
        }

        log.info("Azure Blob Storage uploader initialized: container={}",
                config.getContainerName());
    }

    /**
     * Upload string content as a blob to Azure Storage.
     *
     * @param content  the content to upload
     * @param blobName the blob name (path within container)
     */
    public void upload(String content, String blobName) {
        try {
            var fullPath = buildPath(blobName);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

            log.info("Uploading blob to container={}, path={}, size={} bytes",
                    config.getContainerName(), fullPath, bytes.length);

            BlobClient blobClient = containerClient.getBlobClient(fullPath);
            blobClient.upload(new ByteArrayInputStream(bytes), bytes.length, true);

            blobClient.setHttpHeaders(new BlobHttpHeaders()
                    .setContentType("application/octet-stream"));

            log.info("Successfully uploaded blob: container={}, path={}",
                    config.getContainerName(), fullPath);
        } catch (Exception e) {
            log.error("Failed to upload blob '{}' to Azure: {}", blobName, e.getMessage(), e);
            throw new RuntimeException("Azure Blob upload failed", e);
        }
    }

    /**
     * Upload an input stream to Azure Blob Storage.
     *
     * @param inputStream the data stream to upload
     * @param blobName    the blob name (path within container)
     * @param length      the content length in bytes
     */
    public void upload(InputStream inputStream, String blobName, long length) {
        try {
            var fullPath = buildPath(blobName);
            log.info("Uploading blob stream to container={}, path={}, size={} bytes",
                    config.getContainerName(), fullPath, length);

            BlobClient blobClient = containerClient.getBlobClient(fullPath);
            blobClient.upload(inputStream, length, true);

            log.info("Successfully uploaded blob stream: container={}, path={}",
                    config.getContainerName(), fullPath);
        } catch (Exception e) {
            log.error("Failed to upload blob stream '{}': {}", blobName, e.getMessage(), e);
            throw new RuntimeException("Azure Blob upload failed", e);
        }
    }

    private String buildPath(String blobName) {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "/" + blobName;
    }
}
