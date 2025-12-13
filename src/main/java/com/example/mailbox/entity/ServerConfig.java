package com.example.mailbox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "server_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerConfig {
    @Id
    @Column(name = "config_key", nullable = false, unique = true)
    private String key;

    @Column(name = "config_value")
    private String value;

    private String description;
}