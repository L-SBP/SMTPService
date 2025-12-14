package com.example.mailbox.controller;

import com.example.mailbox.entity.EmailQueue;
import com.example.mailbox.service.EmailQueueService;
import com.example.mailbox.vo.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 邮件队列管理控制器
 * 用于管理邮件发送失败后的重试队列
 */
@RestController
@RequestMapping("/api/queue")
@PreAuthorize("hasRole('ADMIN')") // 仅管理员可访问
@Slf4j
public class QueueController {

    @Autowired
    private EmailQueueService emailQueueService;

    /**
     * 获取队列状态
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<List<EmailQueue>>> getQueueStatus() {
        List<EmailQueue> queueItems = emailQueueService.getQueueStatus();
        return ResponseEntity.ok(new ApiResponse<>(true, queueItems, "获取队列状态成功", null));
    }

    /**
     * 重新发送指定邮件
     */
    @PostMapping("/resend/{queueId}")
    public ResponseEntity<ApiResponse<String>> resendEmail(@PathVariable Long queueId) {
        ApiResponse<String> result = emailQueueService.resendEmail(queueId);
        return ResponseEntity.ok(result);
    }

    /**
     * 清空队列
     */
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<String>> clearQueue() {
        emailQueueService.clearQueue();
        return ResponseEntity.ok(new ApiResponse<>(true, "队列已清空", "清空成功", null));
    }

    /**
     * 手动触发队列处理
     */
    @PostMapping("/process")
    public ResponseEntity<ApiResponse<String>> processQueue() {
        emailQueueService.processQueue();
        return ResponseEntity.ok(new ApiResponse<>(true, "队列处理完成", "处理成功", null));
    }
}
