package com.example.zenith.controller;

import com.example.zenith.entity.AuditLog;
import com.example.zenith.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/audits")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String search,

            @RequestParam(required = false)
            @DateTimeFormat(pattern = "d/M/yyyy") LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(pattern = "d/M/yyyy") LocalDate endDate,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        if (page < 0) {
            throw new IllegalArgumentException("Page index cannot be less than zero.");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Page size must be at least 1.");
        }

        Pageable pageable = PageRequest.of(page,size,Sort.by(Sort.Direction.fromString(sortDir), sortBy)
        );

        return ResponseEntity.ok(
                auditService.getAuditLogs(search, startDate, endDate, pageable)
        );
    }
}