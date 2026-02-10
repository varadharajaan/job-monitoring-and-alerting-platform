package com.jobmonitor.platform.common.functional;

import java.util.Map;

/**
 * Functional interface for dispatching notifications through a specific channel.
 * Implementations handle EMAIL, SLACK, SMS, PUSH, etc.
 */
@FunctionalInterface
public interface NotificationDispatcher {

    /**
     * Send a notification to the given recipient.
     *
     * @param recipient the target recipient (email address, Slack channel, phone, etc.)
     * @param subject   the notification subject
     * @param body      the rendered notification body
     * @param metadata  additional metadata for delivery tracking
     * @return the provider message ID on success
     */
    String dispatch(String recipient, String subject, String body, Map<String, String> metadata);
}
