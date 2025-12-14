package com.example.mailbox.repository;

import com.example.mailbox.entity.EmailQueue;
import com.example.mailbox.entity.EmailQueue.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件队列数据访问接口
 */
@Repository
public interface EmailQueueRepository extends JpaRepository<EmailQueue, Long> {
    
    /**
     * 查找待处理且到期的队列项
     */
    List<EmailQueue> findByStatusAndNextRetryTimeBefore(Status status, LocalDateTime time);
    
    /**
     * 根据状态查找队列项
     */
    List<EmailQueue> findByStatusOrderByCreatedAtAsc(Status status);
    
    /**
     * 根据邮件ID查找队列项
     */
    List<EmailQueue> findByEmailId(Long emailId);
    
    /**
     * 查找所有队列项并按创建时间排序
     */
    List<EmailQueue> findAllByOrderByCreatedAtDesc();
    
    /**
     * 统计各状态的队列项数量
     */
    @Query("SELECT e.status, COUNT(e) FROM EmailQueue e GROUP BY e.status")
    List<Object[]> countByStatus();
    
    /**
     * 查找超过最大重试次数的队列项
     */
    @Query("SELECT e FROM EmailQueue e WHERE e.retryCount >= :maxRetry AND e.status = :status")
    List<EmailQueue> findByRetryCountGreaterThanEqualAndStatus(
            @Param("maxRetry") int maxRetry, 
            @Param("status") Status status);
}
