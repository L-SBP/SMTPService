package com.example.mailbox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "signature")
    private String signature;

    @Column(name = "is_admin")
    private Boolean isAdmin = false;

    // --- 新增：账号启用状态 ---
    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "quota_limit")
    private Double quotaLimit = 100.0;

    @Column(name = "used_space")
    private Double usedSpace = 0.0;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // --- 修改：关联数据库字段 ---
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    // 手动 Getter/Setter 确保兼容性
    public Boolean getIsAdmin() { return isAdmin; }
    public void setIsAdmin(Boolean isAdmin) { this.isAdmin = isAdmin; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
