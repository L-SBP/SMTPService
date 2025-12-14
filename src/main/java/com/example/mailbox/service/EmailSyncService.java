package com.example.mailbox.service;

import com.example.mailbox.entity.Account;
import com.example.mailbox.vo.ApiResponse;

/**
 * 邮件同步服务接口
 * 负责定时同步邮件和管理邮件同步状态
 */
public interface EmailSyncService {
    
    /**
     * 同步指定用户的邮件
     * @param user 用户账户
     * @return 同步结果
     */
    ApiResponse<String> syncEmails(Account user);
    
    /**
     * 同步所有用户的邮件
     */
    void syncAllUsersEmails();
    
    /**
     * 获取用户的同步状态
     * @param userId 用户ID
     * @return 同步状态
     */
    SyncStatus getSyncStatus(Long userId);
    
    /**
     * 同步状态枚举
     */
    enum SyncStatus {
        IDLE,           // 空闲
        SYNCING,        // 同步中
        ERROR,          // 错误
        SUCCESS         // 成功
    }
}
