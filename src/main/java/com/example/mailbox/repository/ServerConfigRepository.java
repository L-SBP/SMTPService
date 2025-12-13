package com.example.mailbox.repository;

import com.example.mailbox.entity.ServerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServerConfigRepository extends JpaRepository<ServerConfig, String> {
    Optional<ServerConfig> findByKey(String key);
}