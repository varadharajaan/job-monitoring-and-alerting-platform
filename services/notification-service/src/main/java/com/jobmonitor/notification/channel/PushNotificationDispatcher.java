package com.jobmonitor.notification.channel;

import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Push notification dispatcher.
 * <p>
 * In production, integrates with Firebase Cloud Messaging (FCM) or Azure Notification Hubs:
 * - Set {@code FCM_SERVER_KEY} or use Azure Notification Hubs connection string
 * <p>
 * For local development, logs the push notification.
 */
@Slf4j
@Component("pushDispatcher")
public class PushNotificationDispatcher implements NotificationDispatcher {

    @Override
    public String dispatch(String recipient, String subject, String body,
                           Map<String, String> metadata) {
        var providerId = "push-" + UUID.randomUUID();

        // In production, this would call FCM or Azure Notification Hubs:
        // FirebaseMessaging.getInstance().send(Message.builder()
        //     .setToken(recipient)
        //     .setNotification(Notification.builder().setTitle(subject).setBody(body).build())
        //     .build());
        log.info("Push notification dispatched to deviceToken={}, subject='{}', providerId={}",
                truncateToken(recipient), subject, providerId);

        return providerId;
    }

    private String truncateToken(String token) {
        if (token != null && token.length() > 12) {
            return token.substring(0, 12) + "...";
        }
        return token;
    }
}
