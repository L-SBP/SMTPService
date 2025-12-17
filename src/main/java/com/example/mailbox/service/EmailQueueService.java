package com.example.mailbox.service;

import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.EmailQueue;
import com.example.mailbox.dto.EmailQueueStatusDTO;
import com.example.mailbox.vo.ApiResponse;

import java.util.List;

/**
 * 邮件队列服务接口
 * 负责管理邮件发送失败后的重试机制
 */
public interface EmailQueueService {
    
    /**
     * 添加邮件到重试队列
     */
    EmailQueue addToQueue(Email email, String errorMessage, int retryCount);
    
    /**
     * 处理队列中的邮件
     */
    void processQueue();
    
    /**
     * 重新发送指定邮件
     */
    ApiResponse<String> resendEmail(Long queueId);
    
    /**
     * 获取队列状态
     */
    List<EmailQueueStatusDTO> getQueueStatus();
    
    /**
     * 清空队列
     */
    void clearQueue();
}
