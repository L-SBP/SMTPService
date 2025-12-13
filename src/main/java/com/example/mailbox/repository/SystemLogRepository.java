package com.example.mailbox.repository;

import com.example.mailbox.entity.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    // 继承基本的增删改查功能
}