package com.example.zenith.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long actorId; // The userId of who initiated the action

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ActionType actionType;

    @Column(updatable = false)
    private String ipAddress;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String payload; // Stores JSON of the request or error details

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
}