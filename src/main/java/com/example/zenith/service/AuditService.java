package com.example.zenith.service;

import com.example.zenith.entity.ActionType;
import com.example.zenith.entity.AuditLog;
import com.example.zenith.repositoy.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(Long actorId, ActionType actionType, String payload) {

        AuditLog log = AuditLog.builder()
                .actorId(actorId)
                .actionType(actionType)
                .payload(payload)
                .ipAddress(getClientIp())
                .build();

        auditLogRepository.save(log);
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
            return "SYSTEM";
        }
    }
}