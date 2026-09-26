package com.example.zenith.repositoy;

import com.example.zenith.entity.ActionType;
import com.example.zenith.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.actorId = :actorId AND a.actionType IN :actionTypes AND a.timestamp >= :since")
    long countRecentActions(
            @Param("actorId") Long actorId,
            @Param("actionTypes") List<ActionType> actionTypes,
            @Param("since") LocalDateTime since
    );

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.actorId = :actorId AND a.actionType IN :actionTypes AND a.payload LIKE CONCAT('%', :keyword, '%') AND a.timestamp >= :since")
    long countRecentActionsByKeyword(
            @Param("actorId") Long actorId,
            @Param("actionTypes") List<ActionType> actionTypes,
            @Param("keyword") String keyword,
            @Param("since") LocalDateTime since
    );
    Page<AuditLog> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Page<AuditLog> findByTimestampGreaterThanEqual(LocalDateTime startDate, Pageable pageable);
}