package com.example.zenith.service;

import com.example.zenith.entity.ActionType;
import com.example.zenith.entity.AuditLog;
import com.example.zenith.repositoy.AuditLogRepository;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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
    public Page<AuditLog> getAuditLogs(String search, LocalDate startDate, LocalDate endDate, Pageable pageable) {

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Invalid date range: startDate cannot be after endDate");
        }

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.toLowerCase() + "%";
                List<Predicate> searchPredicates = new ArrayList<>();

                searchPredicates.add(cb.like(cb.lower(root.get("payload")), searchPattern));
                searchPredicates.add(cb.like(cb.lower(root.get("ipAddress")), searchPattern));
                searchPredicates.add(cb.like(cb.lower(root.get("actionType").as(String.class)), searchPattern));

                try {
                    Long actorIdSearch = Long.parseLong(search.trim());
                    searchPredicates.add(cb.equal(root.get("actorId"), actorIdSearch));
                } catch (NumberFormatException e) {
                }

             
                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), endDate.atTime(LocalTime.MAX)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable);
    }

    private String getClientIp() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                    .currentRequestAttributes()).getRequest();

            System.out.println("IP" + request.getRemoteAddr());
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