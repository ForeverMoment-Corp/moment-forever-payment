package com.forvmom.MomentForeverPayment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    public void sendAlert(String message) {
        // In production, send to Slack, PagerDuty, email, etc.
        log.error("ALERT: {}", message);

        // You can implement actual alerting here
        // - Send to Slack webhook
        // - Send to PagerDuty
        // - Send email to ops team
        // - Push to monitoring system
    }
}