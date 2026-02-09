package com.jobmonitor.platform.common.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Logging-specific properties bound from {@code platform.logging.*}.
 * Added as a nested class in {@link PlatformProperties}.
 */
@Getter
@Setter
public class LoggingProperties {
    private String dir = "logs";
    private String maxFileSize = "50MB";
    private int maxHistory = 30;
    private String totalSizeCap = "1GB";
}
