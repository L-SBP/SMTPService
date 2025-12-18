package com.example.mailbox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false)
    private LogType type; // SMTP, POP3, SYSTEM, ADMIN

    @Column(name = "log_operator", nullable = false)
    private String operator; // 操作者IP或用户名

    @Column(name = "log_action", nullable = false)
    private String action; // 操作名称 (e.g., LOGIN, SEND_MAIL)

    @Column(length = 1000)
    private String details; // 详情描述

    @Column(name = "log_status", nullable = false)
    private String status; // SUCCESS, FAILURE

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum LogType {
        SMTP, POP3, SYSTEM, ADMIN, OUTBOUND, INBOUND
    }
}