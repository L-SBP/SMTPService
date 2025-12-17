package com.example.mailbox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邮件队列实体
 * 用于管理邮件发送失败后的重试机制
 */
@Entity
@Table(name = "email_queue")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailQueue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "email_id", nullable = false)
    private Long emailId;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "queue_status", nullable = false)
    private Status status;
    
    @Column(name = "next_retry_time")
    private LocalDateTime nextRetryTime;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * 队列状态枚举
     */
    public enum Status {
        PENDING,    // 待处理
        PROCESSING, // 处理中
        COMPLETED,  // 已完成
        FAILED      // 失败
    }
}
