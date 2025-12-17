package com.example.mailbox.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @Column(name = "type", nullable = false)
    private Type type; // IP 或 EMAIL

    @Column(name = "value", nullable = false, unique = true)
    private String value; // 具体 IP 地址或邮箱地址

    @JsonIgnore
    @Enumerated(EnumType.STRING)
    @Column(name = "bl_type", nullable = false)
    private Type legacyType;

    @JsonIgnore
    @Column(name = "bl_value", nullable = false, unique = true)
    private String legacyValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        syncLegacyColumns();
    }

    @PreUpdate
    protected void onUpdate() {
        syncLegacyColumns();
    }

    @PostLoad
    protected void onLoad() {
        if (type == null && legacyType != null) {
            type = legacyType;
        }
        if (value == null && legacyValue != null) {
            value = legacyValue;
        }
    }

    public void setType(Type type) {
        this.type = type;
        this.legacyType = type;
    }

    public void setValue(String value) {
        this.value = value;
        this.legacyValue = value;
    }

    private void syncLegacyColumns() {
        if (legacyType == null && type != null) {
            legacyType = type;
        }
        if (legacyValue == null && value != null) {
            legacyValue = value;
        }
        if (type == null && legacyType != null) {
            type = legacyType;
        }
        if (value == null && legacyValue != null) {
            value = legacyValue;
        }
    }

    public enum Type {
        IP, EMAIL
    }
}
