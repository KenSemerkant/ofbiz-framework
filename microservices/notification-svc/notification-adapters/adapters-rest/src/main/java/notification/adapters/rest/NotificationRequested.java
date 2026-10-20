package notification.adapters.rest;

import java.util.Map;

/** Wire shape of notification.requested.v1. */
record NotificationRequested(
        String notificationId,
        String channel,
        String recipient,
        String template,
        String locale,
        Map<String, String> params) {}
