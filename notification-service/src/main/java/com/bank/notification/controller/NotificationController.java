package com.bank.notification.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.notification.service.NotificationService;

import java.util.List;
import java.util.Map;

/**
 * REST controller for notification service.
 * Serves the notification outbox the UI reads (GET /api/notifications via the
 * gateway) plus a health-check endpoint.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public List<Map<String, Object>> listNotifications(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String causeEvent) {
        return notificationService.listNotifications(accountId, causeEvent);
    }

    /**
     * Health check endpoint (legacy path).
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Notification Service is running");
    }
}
