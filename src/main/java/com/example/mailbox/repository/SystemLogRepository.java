package com.example.mailbox.repository;

import com.example.mailbox.entity.SystemLog;
import com.example.mailbox.entity.SystemLog.LogType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {

    // 按类型查询日志
    Page<SystemLog> findByType(LogType type, Pageable pageable);

    // 按状态查询日志
    Page<SystemLog> findByStatus(String status, Pageable pageable);

    // 按类型和状态查询
    Page<SystemLog> findByTypeAndStatus(LogType type, String status, Pageable pageable);

    // 按时间范围查询
    Page<SystemLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // 按类型和时间范围查询
    Page<SystemLog> findByTypeAndCreatedAtBetween(LogType type, LocalDateTime start, LocalDateTime end,
            Pageable pageable);

    // 统计各类型日志数量
    @Query("SELECT l.type, COUNT(l) FROM SystemLog l GROUP BY l.type")
    List<Object[]> countByType();

    // 统计各状态日志数量
    @Query("SELECT l.status, COUNT(l) FROM SystemLog l GROUP BY l.status")
    List<Object[]> countByStatus();

    // 统计指定类型的成功/失败数量
    @Query("SELECT l.status, COUNT(l) FROM SystemLog l WHERE l.type = :type GROUP BY l.status")
    List<Object[]> countByTypeGroupByStatus(@Param("type") LogType type);

    // 统计最近N小时内的日志数量
    @Query("SELECT COUNT(l) FROM SystemLog l WHERE l.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    // 统计最近N小时内指定类型的日志数量
    @Query("SELECT COUNT(l) FROM SystemLog l WHERE l.type = :type AND l.createdAt >= :since")
    long countByTypeSince(@Param("type") LogType type, @Param("since") LocalDateTime since);

    // 删除指定时间之前的日志
    void deleteByCreatedAtBefore(LocalDateTime before);
}