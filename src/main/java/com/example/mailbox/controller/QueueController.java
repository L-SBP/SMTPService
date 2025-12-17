package com.example.mailbox.controller;

import com.example.mailbox.dto.EmailQueueStatusDTO;
import com.example.mailbox.entity.EmailQueue;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.EmailQueueService;
import com.example.mailbox.service.TokenService;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.vo.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 邮件队列管理控制器
 * 用于管理邮件发送失败后的重试队列
 */
@RestController
@RequestMapping("/api/queue")
@Slf4j
public class QueueController {

    @Autowired
    private EmailQueueService emailQueueService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenService tokenService;

    private void requireAdmin(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("未提供认证信息");
        }

        String token = authHeader.substring(7);
        String identifier = jwtUtil.extractUsername(token);
        if (identifier == null || !jwtUtil.validateToken(token, identifier) || !tokenService.hasToken(token)) {
            throw new RuntimeException("认证信息无效");
        }

        var operator = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (Boolean.FALSE.equals(operator.getEnabled())) {
            throw new RuntimeException("账号已被禁用");
        }

        if (!Boolean.TRUE.equals(operator.getIsAdmin())) {
            throw new RuntimeException("无管理员权限");
        }
    }

    /**
     * 获取队列状态
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<List<EmailQueueStatusDTO>>> getQueueStatus(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
            List<EmailQueueStatusDTO> queueItems = emailQueueService.getQueueStatus();
            return ResponseEntity.ok(new ApiResponse<>(true, queueItems, "获取队列状态成功", null));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    /**
     * 重新发送指定邮件
     */
    @PostMapping("/resend/{queueId}")
    public ResponseEntity<ApiResponse<String>> resendEmail(HttpServletRequest httpRequest, @PathVariable Long queueId) {
        try {
            requireAdmin(httpRequest);
            ApiResponse<String> result = emailQueueService.resendEmail(queueId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    /**
     * 清空队列
     */
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<String>> clearQueue(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
            emailQueueService.clearQueue();
            return ResponseEntity.ok(new ApiResponse<>(true, "队列已清空", "清空成功", null));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    /**
     * 手动触发队列处理
     */
    @PostMapping("/process")
    public ResponseEntity<ApiResponse<String>> processQueue(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
            emailQueueService.processQueue();
            return ResponseEntity.ok(new ApiResponse<>(true, "队列处理完成", "处理成功", null));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }
}
