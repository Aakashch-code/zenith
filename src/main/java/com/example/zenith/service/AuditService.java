package com.example.zenith.service;

import com.example.zenith.entity.ActionType;
import com.example.zenith.entity.AuditLog;

import com.example.zenith.repositoy.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(Long actorId, ActionType actionType, String payload) {
        if (actorId == null) {
            throw new IllegalArgumentException("Audit log failure: Actor ID cannot be null");
        }
        if (actionType == null) {
            throw new IllegalArgumentException("Audit log failure: Action type cannot be null");
        }

        AuditLog auditLog = AuditLog.builder()
                .actorId(actorId)
                .actionType(actionType)
                .payload(payload)
                .ipAddress(getClientIp())
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(LocalDate startDate, LocalDate endDate, Pageable pageable) {

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Invalid date range: startDate cannot be after endDate");
        }

        LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(LocalTime.MAX) : null;

        if (startDateTime != null && endDateTime != null) {
            return auditLogRepository.findByTimestampBetween(startDateTime, endDateTime, pageable);
        }

        if (startDateTime != null) {
            return auditLogRepository.findByTimestampGreaterThanEqual(startDateTime, pageable);
        }

        return auditLogRepository.findAll(pageable);
    }

    private String getClientIp() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                    .currentRequestAttributes()).getRequest();

            String xfHeader = request.getHeader("X-Forwarded-For");
            if (xfHeader == null) {
                return request.getRemoteAddr();
            }
            return xfHeader.split(",")[0];
        } catch (Exception e) {
            log.warn("Failed to retrieve client IP from request context. Defaulting to 'SYSTEM'. Error: {}", e.getMessage());
            return "SYSTEM";
        }
    }
}