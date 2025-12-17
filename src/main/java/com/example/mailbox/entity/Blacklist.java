package com.example.mailbox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "blacklist")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Blacklist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "bl_type", nullable = false)
    private Type type; // IP 或 EMAIL

    @Column(name = "bl_value", nullable = false, unique = true)
    private String value; // 具体 IP 地址或邮箱地址

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Type {
        IP, EMAIL
    }
}