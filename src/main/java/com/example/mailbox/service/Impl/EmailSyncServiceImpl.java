package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Email;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.service.EmailProtocolService;
import com.example.mailbox.service.EmailService;
import com.example.mailbox.service.EmailSyncService;
import com.example.mailbox.vo.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 邮件同步服务实现类
 * 负责定时同步邮件和管理邮件同步状态
 */
@Service
@Slf4j
public class EmailSyncServiceImpl implements EmailSyncService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailProtocolService emailProtocolService;

    // 同步状态管理
    private final ConcurrentHashMap<Long, SyncStatus> syncStatusMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Lock> userLocks = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public ApiResponse<String> syncEmails(Account user) {
        Long userId = user.getId();
        Lock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        
        if (!lock.tryLock()) {
            return new ApiResponse<>(false, null, "用户正在同步中，请稍后重试", null);
        }
        
        try {
            // 设置同步状态
            syncStatusMap.put(userId, SyncStatus.SYNCING);
            log.info("开始同步用户 {} 的邮件", user.getEmail());

            // 获取邮件服务器配置
            EmailProtocolService.Pop3ServerConfig config = emailProtocolService.getPop3Config(user.getEmail());
            
            // TODO: 需要安全地获取用户密码
            // 课程作业中，需要实现密码获取逻辑
            String password = getPasswordForUser(user);
            
            if (password == null) {
                syncStatusMap.put(userId, SyncStatus.ERROR);
                return new ApiResponse<>(false, null, "无法获取用户密码，请检查账户配置", null);
            }

            // 接收邮件 - 使用协议服务直接接收
            List<Email> receivedEmails = emailProtocolService.receiveEmails(
                user.getEmail(), password, config.getHost(), config.getPort(), config.isSsl()
            );
            
            // 保存邮件到数据库
            for (Email emailEntity : receivedEmails) {
                // 检查邮件是否已存在（通过发件人、主题和时间判断）
                boolean exists = emailRepository.existsBySenderAndSubjectAndReceivedTime(
                    emailEntity.getSender(), 
                    emailEntity.getSubject(), 
                    emailEntity.getReceivedTime()
                );
                
                if (!exists) {
                    emailEntity.setUser(user);
                    emailRepository.save(emailEntity);
                }
            }
            
            syncStatusMap.put(userId, SyncStatus.SUCCESS);
            log.info("用户 {} 的邮件同步完成", user.getEmail());
            
            return new ApiResponse<>(true, "邮件同步成功", "成功同步邮件", null);
            
        } catch (Exception e) {
            syncStatusMap.put(userId, SyncStatus.ERROR);
            log.error("同步用户 {} 的邮件失败: {}", user.getEmail(), e.getMessage(), e);
            return new ApiResponse<>(false, null, "邮件同步失败: " + e.getMessage(), null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Async
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public List<ApiResponse<String>> syncAllUsersEmails() {
        List<ApiResponse<String>> results = new ArrayList<>();
        
        List<Account> users = userRepository.findAll();
        log.info("开始批量同步，共有 {} 个用户", users.size());
        
        for (Account user : users) {
            try {
                ApiResponse<String> result = syncEmails(user);
                results.add(result);
            } catch (Exception e) {
                log.error("同步用户 {} 的邮件时发生异常: {}", user.getEmail(), e.getMessage());
                results.add(new ApiResponse<>(false, null, "同步异常: " + e.getMessage(), null));
            }
        }
        
        log.info("批量同步完成，共处理 {} 个用户", results.size());
        return results;
    }

    @Override
    public SyncStatus getSyncStatus(Long userId) {
        return syncStatusMap.getOrDefault(userId, SyncStatus.IDLE);
    }

    /**
     * 获取用户密码（课程作业：需要根据实际情况实现）
     * @param user 用户账户
     * @return 用户密码
     */
    private String getPasswordForUser(Account user) {
        // TODO: 实现安全的密码获取逻辑
        // 课程作业中，可以：
        // 1. 从数据库中获取加密的密码
        // 2. 使用明文密码（不推荐，仅用于测试）
        // 3. 使用配置文件中的密码
        // 4. 使用OAuth2令牌
        
        // 目前返回null，表示需要实现
        log.warn("需要实现密码获取逻辑");
        return null;
    }

    /**
     * 手动触发同步
     */
    public ApiResponse<String> triggerSync(Long userId) {
        Account user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        return syncEmails(user);
    }
}
