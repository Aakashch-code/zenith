package com.example.zenith.service;

import com.example.zenith.entity.ActionType;
import com.example.zenith.exception.FraudSuspicionException;
import com.example.zenith.repositoy.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    private static final BigDecimal LARGE_TX_THRESHOLD = new BigDecimal("200000");
    private static final int RAPID_TX_LIMIT = 5;
    private static final int MAX_FAILED_OPS = 4;
    private static final int MAX_INSUFFICIENT_FUNDS = 3;
    private static final int TIME_WINDOW_MINUTES = 5;

    public void evaluateTransaction(Long userId, BigDecimal amount) {
        LocalDateTime timeWindow = LocalDateTime.now().minusMinutes(TIME_WINDOW_MINUTES);

        if (amount != null && amount.compareTo(LARGE_TX_THRESHOLD) > 0) {
            triggerAlert(userId, "Large transaction attempt: " + amount + " exceeds threshold.");
        }

        long recentSuccesses = auditLogRepository.countRecentActions(
                userId,
                List.of(ActionType.TRANSFER_COMPLETED, ActionType.DEPOSIT_COMPLETED, ActionType.WITHDRAWAL_COMPLETED),
                timeWindow
        );
        if (recentSuccesses >= RAPID_TX_LIMIT) {
            triggerAlert(userId, "Rapid activity: " + recentSuccesses + " successful transactions in " + TIME_WINDOW_MINUTES + " mins.");
        }

        long recentFailures = auditLogRepository.countRecentActions(
                userId,
                List.of(ActionType.TRANSFER_FAILED, ActionType.DEPOSIT_FAILED, ActionType.WITHDRAWAL_FAILED),
                timeWindow
        );
        if (recentFailures >= MAX_FAILED_OPS) {
            triggerAlert(userId, "High failure rate: " + recentFailures + " failed operations in " + TIME_WINDOW_MINUTES + " mins.");
        }

        long nsfAttempts = auditLogRepository.countRecentActionsByKeyword(
                userId,
                List.of(ActionType.TRANSFER_FAILED, ActionType.WITHDRAWAL_FAILED),
                "Insufficient",
                timeWindow
        );
        if (nsfAttempts >= MAX_INSUFFICIENT_FUNDS) {
            triggerAlert(userId, "Insufficient balance abuse: " + nsfAttempts + " failed attempts to overdraw in " + TIME_WINDOW_MINUTES + " mins.");
        }
    }

    private void triggerAlert(Long userId, String reason) {
        log.warn("FRAUD INTERVENTION [User {}]: {}", userId, reason);
        auditService.logAction(userId, ActionType.SUSPICIOUS_ACTIVITY_FLAGGED, "Blocked: " + reason);
        throw new FraudSuspicionException("Transaction declined due to suspicious security patterns. Please contact support.");
    }
}