package com.example.mailbox.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 邮件配置类
 * 启用异步和定时任务功能
 */
@Configuration
@EnableAsync
@EnableScheduling
public class EmailConfig {
    // 配置类，用于启用Spring的异步和定时任务功能
}
