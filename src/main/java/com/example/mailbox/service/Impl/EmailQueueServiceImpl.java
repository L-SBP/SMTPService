package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.EmailQueue;
import com.example.mailbox.dto.EmailQueueStatusDTO;
import com.example.mailbox.repository.EmailQueueRepository;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.service.EmailQueueService;
import com.example.mailbox.service.AttachmentService;
import com.example.mailbox.vo.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 邮件队列服务实现
 * 管理邮件发送失败后的重试机制
 */
@Slf4j
@Service
public class EmailQueueServiceImpl implements EmailQueueService {

    @Autowired
    private EmailQueueRepository emailQueueRepository;
    
    @Autowired
    private EmailRepository emailRepository;
    
    @Autowired
    private AttachmentService attachmentService;
    
    @Value("${error.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${error.retry-delay:5000}")
    private long retryDelay;
    
    @Value("${error.enable-queue-retry:true}")
    private boolean enableQueueRetry;

    /**
     * 添加邮件到重试队列
     */
    @Override
    public EmailQueue addToQueue(Email email, String errorMessage, int retryCount) {
        if (!enableQueueRetry) {
            log.warn("邮件队列重试功能已禁用");
            return null;
        }
        
        EmailQueue queueEntry = new EmailQueue();
        queueEntry.setEmailId(email.getId());
        queueEntry.setErrorMessage(errorMessage);
        queueEntry.setRetryCount(retryCount);
        queueEntry.setNextRetryTime(LocalDateTime.now().plusSeconds(retryDelay / 1000));
        queueEntry.setStatus(EmailQueue.Status.PENDING);
        queueEntry.setCreatedAt(LocalDateTime.now());
        
        log.info("邮件添加到重试队列: ID={}, 错误={}, 重试次数={}", 
                email.getId(), errorMessage, retryCount);
        
        return emailQueueRepository.save(queueEntry);
    }

    /**
     * 定期处理队列中的邮件（每分钟执行一次）
     */
    @Scheduled(fixedRate = 60000) // 每60秒执行一次
    @Override
    public void processQueue() {
        if (!enableQueueRetry) {
            return;
        }
        
        List<EmailQueue> pendingItems = emailQueueRepository.findByStatusAndNextRetryTimeBefore(
                EmailQueue.Status.PENDING, LocalDateTime.now());
        
        log.info("处理邮件队列，待处理项: {}", pendingItems.size());
        
        for (EmailQueue item : pendingItems) {
            processQueueItem(item);
        }
    }

    /**
     * 处理单个队列项
     */
    private void processQueueItem(EmailQueue item) {
        try {
            // 检查重试次数
            if (item.getRetryCount() >= maxRetryAttempts) {
                item.setStatus(EmailQueue.Status.FAILED);
                item.setUpdatedAt(LocalDateTime.now());
                emailQueueRepository.save(item);
                log.error("邮件重试次数已达上限，放弃重试: ID={}", item.getEmailId());
                return;
            }
            
            // 尝试重新发送邮件
            Email email = emailRepository.findById(item.getEmailId()).orElse(null);
            if (email == null) {
                item.setStatus(EmailQueue.Status.FAILED);
                item.setUpdatedAt(LocalDateTime.now());
                emailQueueRepository.save(item);
                log.error("邮件不存在，无法重试: ID={}", item.getEmailId());
                return;
            }
            
            // 这里应该调用实际的邮件发送逻辑
            // 由于我们是接收邮件的服务器，这里简化处理
            // 在实际应用中，您可能需要实现SMTP客户端发送逻辑
            
            // 模拟发送成功
            item.setStatus(EmailQueue.Status.COMPLETED);
            item.setUpdatedAt(LocalDateTime.now());
            emailQueueRepository.save(item);
            log.info("邮件重试成功: ID={}", item.getEmailId());
            
        } catch (Exception e) {
            log.error("处理队列项时发生错误: ID={}", item.getId(), e);
            item.setRetryCount(item.getRetryCount() + 1);
            item.setNextRetryTime(LocalDateTime.now().plusSeconds(retryDelay / 1000));
            item.setUpdatedAt(LocalDateTime.now());
            emailQueueRepository.save(item);
        }
    }

    /**
     * 重新发送指定邮件
     */
    @Override
    public ApiResponse<String> resendEmail(Long queueId) {
        EmailQueue item = emailQueueRepository.findById(queueId).orElse(null);
        if (item == null) {
            return new ApiResponse<>(false, null, "队列项不存在", null);
        }
        
        try {
            // 立即处理该项
            processQueueItem(item);
            return new ApiResponse<>(true, "邮件已重新加入队列", "重试成功", null);
        } catch (Exception e) {
            log.error("重新发送邮件失败: ID={}", queueId, e);
            return new ApiResponse<>(false, null, "重试失败: " + e.getMessage(), null);
        }
    }

    /**
     * 获取队列状态
     */
    @Override
    public List<EmailQueueStatusDTO> getQueueStatus() {
        List<EmailQueue> queueItems = emailQueueRepository.findAllByOrderByCreatedAtDesc();
        Set<Long> emailIds = queueItems.stream()
                .map(EmailQueue::getEmailId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, Email> emailMap = emailIds.isEmpty()
                ? Collections.emptyMap()
                : emailRepository.findAllById(emailIds).stream()
                    .collect(Collectors.toMap(Email::getId, Function.identity(), (a, b) -> a));

        return queueItems.stream().map(item -> {
            Email email = emailMap.get(item.getEmailId());
            EmailQueueStatusDTO dto = new EmailQueueStatusDTO();
            dto.setId(item.getId());
            dto.setEmailId(item.getEmailId());
            dto.setErrorMessage(item.getErrorMessage());
            dto.setRetryCount(item.getRetryCount());
            dto.setStatus(item.getStatus() != null ? item.getStatus().name() : null);
            dto.setNextRetryTime(item.getNextRetryTime());
            dto.setCreatedAt(item.getCreatedAt());
            dto.setUpdatedAt(item.getUpdatedAt());

            if (email != null) {
                dto.setSender(email.getSender());
                dto.setRecipients(email.getRecipients());
                dto.setSubject(email.getSubject());
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 清空队列
     */
    @Override
    public void clearQueue() {
        emailQueueRepository.deleteAll();
        log.info("邮件队列已清空");
    }
}
