package com.bank.notification.controller;

import com.bank.notification.model.NotificationView;
import com.bank.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Notification outbox read API (docs/api_spec/notification-service.yaml). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<NotificationView> list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String causeEvent) {
        return notifications.list(accountId, causeEvent);
    }
}
