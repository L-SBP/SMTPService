package com.example.mailbox.controller;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Blacklist;
import com.example.mailbox.repository.BlacklistRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.server.EnhancedPop3Server;
import com.example.mailbox.server.EnhancedSmtpServer;
import com.example.mailbox.vo.ApiResponse;
import com.example.mailbox.vo.Page;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.service.TokenService;
import java.util.stream.Collectors;

/**
 * 管理员控制器 - 处理管理员相关操作
 * 使用自定义 JWT 认证，不依赖 Spring Security
 */
@RestController
@RequestMapping("/api/admin")
@Slf4j
public class AdminController {

    @Autowired private UserRepository userRepository;
    @Autowired private BlacklistRepository blacklistRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TokenService tokenService;

    // --- 用户管理 ---

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<Account>> createUser(@RequestBody CreateUserRequest request) {
        log.info("管理员创建用户，邮箱：{}", request.getEmail());
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "邮箱已存在", null));
        }

        Account newUser = new Account();
        newUser.setUsername(request.getUsername());
        newUser.setEmail(request.getEmail());
        newUser.setPassword(request.getPassword());
        newUser.setEnabled(true);
        newUser.setIsAdmin(false);
        // 默认配额 100MB
        newUser.setQuotaLimit(100.0);
        newUser.setUsedSpace(0.0);

        Account savedUser = userRepository.save(newUser);
        log.info("用户创建成功，ID：{}", savedUser.getId());
        return ResponseEntity.ok(new ApiResponse<>(true, savedUser, "用户创建成功", null));
    }
    
    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable Long id) {
        log.info("管理员删除用户，ID：{}", id);
        if (!userRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "用户不存在", null));
        }
        userRepository.deleteById(id);
        log.info("用户删除成功，ID：{}", id);
        return ResponseEntity.ok(new ApiResponse<>(true, "用户已删除", "删除成功", null));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<String>> updateUserRole(@PathVariable Long id, @RequestParam Boolean isAdmin) {
        log.info("管理员修改用户权限，用户ID：{}，是否管理员：{}", id, isAdmin);
        return userRepository.findById(id).map(user -> {
            user.setIsAdmin(isAdmin);
            userRepository.save(user);
            String role = isAdmin ? "管理员" : "普通用户";
            log.info("用户权限修改成功，用户ID：{}，新角色：{}", id, role);
            return ResponseEntity.ok(new ApiResponse<>(true, "用户角色已更新为" + role, "操作成功", null));
        }).orElse(ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "用户不存在", null)));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<Account>>> getAllUsers(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("管理员查询用户列表，页码：{}，大小：{}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        var usersPage = userRepository.findAll(pageable);

        Page<Account> responsePage = new Page<>(
                usersPage.getContent(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                usersPage.getSize(),
                usersPage.getNumber(),
                usersPage.isFirst(),
                usersPage.isLast()
        );
        log.info("成功获取用户列表，总数：{}", usersPage.getTotalElements());
        return ResponseEntity.ok(new ApiResponse<>(true, responsePage, "获取用户列表成功", null));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<String>> updateUserStatus(@PathVariable Long id, @RequestParam Boolean enabled) {
        log.info("管理员修改用户状态，用户ID：{}，启用状态：{}", id, enabled);
        return userRepository.findById(id).map(user -> {
            user.setEnabled(enabled);
            userRepository.save(user);
            String status = enabled ? "解封" : "封禁";
            log.info("用户状态修改成功，用户ID：{}，操作：{}", id, status);
            return ResponseEntity.ok(new ApiResponse<>(true, "用户已" + status, "操作成功", null));
        }).orElse(ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "用户不存在", null)));
    }

    // --- 黑名单管理 ---

    @GetMapping("/blacklist")
    public ResponseEntity<ApiResponse<List<Blacklist>>> getBlacklist() {
        log.info("管理员查询黑名单列表");
        List<Blacklist> blacklist = blacklistRepository.findAll();
        log.info("成功获取黑名单，记录数：{}", blacklist.size());
        return ResponseEntity.ok(new ApiResponse<>(true, blacklist, "获取黑名单成功", null));
    }

    @PostMapping("/blacklist")
    public ResponseEntity<ApiResponse<Blacklist>> addToBlacklist(@RequestBody BlacklistRequest request) {
        log.info("管理员添加黑名单，类型：{}，值：{}", request.getType(), request.getValue());
        if (blacklistRepository.existsByTypeAndValue(request.getType(), request.getValue())) {
            log.warn("黑名单记录已存在，类型：{}，值：{}", request.getType(), request.getValue());
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "该记录已存在", null));
        }

        Blacklist blacklist = new Blacklist();
        blacklist.setType(request.getType());
        blacklist.setValue(request.getValue());

        Blacklist saved = blacklistRepository.save(blacklist);
        log.info("黑名单添加成功，ID：{}", saved.getId());
        return ResponseEntity.ok(new ApiResponse<>(true, saved, "添加成功", null));
    }

    @DeleteMapping("/blacklist/{id}")
    public ResponseEntity<ApiResponse<String>> removeFromBlacklist(@PathVariable Long id) {
        log.info("管理员删除黑名单，ID：{}", id);
        blacklistRepository.deleteById(id);
        log.info("黑名单删除成功，ID：{}", id);
        return ResponseEntity.ok(new ApiResponse<>(true, "删除成功", "删除成功", null));
    }

    // --- 服务器管理 ---
    
    @Autowired
    private EnhancedSmtpServer smtpServer;
    
    @Autowired
    private EnhancedPop3Server pop3Server;
    
    @Autowired
    private com.example.mailbox.service.EmailProtocolService emailProtocolService;
    
    @GetMapping("/server/status")
    public ResponseEntity<ApiResponse<String>> getServerStatus() {
        String smtpStatus = smtpServer.isRunning() ? "SMTP服务器运行中" : "SMTP服务器已停止";
        String pop3Status = pop3Server.isRunning() ? "POP3服务器运行中" : "POP3服务器已停止";
        String status = smtpStatus + "，" + pop3Status;
        return ResponseEntity.ok(new ApiResponse<>(true, status, "服务器状态正常", null));
    }

    @PostMapping("/server/smtp/start")
    public ResponseEntity<ApiResponse<String>> startSmtpServer() {
        try {
            if (smtpServer.isRunning()) {
                log.warn("SMTP服务器已在运行中，无需重复启动");
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, null, "SMTP服务器已在运行中", null)
                );
            }
            
            smtpServer.startServer();
            log.info("SMTP服务器启动成功");
            return ResponseEntity.ok(new ApiResponse<>(true, "SMTP服务器启动成功", "服务器已启动", null));
        } catch (Exception e) {
            log.error("启动SMTP服务器失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "启动SMTP服务器失败: " + e.getMessage(), null));
        }
    }

    @PostMapping("/server/smtp/stop")
    public ResponseEntity<ApiResponse<String>> stopSmtpServer() {
        try {
            if (!smtpServer.isRunning()) {
                log.warn("SMTP服务器未在运行，无需停止");
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, null, "SMTP服务器未在运行", null)
                );
            }
            
            smtpServer.stopServer();
            log.info("SMTP服务器停止成功");
            return ResponseEntity.ok(new ApiResponse<>(true, "SMTP服务器停止成功", "服务器已停止", null));
        } catch (Exception e) {
            log.error("停止SMTP服务器失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "停止SMTP服务器失败: " + e.getMessage(), null));
        }
    }

    @PostMapping("/server/pop3/start")
    public ResponseEntity<ApiResponse<String>> startPop3Server() {
        try {
            if (pop3Server.isRunning()) {
                log.warn("POP3服务器已在运行中，无需重复启动");
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, null, "POP3服务器已在运行中", null)
                );
            }
            
            pop3Server.startServer();
            log.info("POP3服务器启动成功");
            return ResponseEntity.ok(new ApiResponse<>(true, "POP3服务器启动成功", "服务器已启动", null));
        } catch (Exception e) {
            log.error("启动POP3服务器失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "启动POP3服务器失败: " + e.getMessage(), null));
        }
    }

    @PostMapping("/server/pop3/stop")
    public ResponseEntity<ApiResponse<String>> stopPop3Server() {
        try {
            if (!pop3Server.isRunning()) {
                log.warn("POP3服务器未在运行，无需停止");
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, null, "POP3服务器未在运行", null)
                );
            }
            
            pop3Server.stopServer();
            log.info("POP3服务器停止成功");
            return ResponseEntity.ok(new ApiResponse<>(true, "POP3服务器停止成功", "服务器已停止", null));
        } catch (Exception e) {
            log.error("停止POP3服务器失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "停止POP3服务器失败: " + e.getMessage(), null));
        }
    }

    @GetMapping("/server/stats")
    public ResponseEntity<ApiResponse<String>> getServerStats() {
        String smtpStats = smtpServer.getStats();
        String pop3Stats = pop3Server.getStats();
        String stats = smtpStats + "；" + pop3Stats;
        return ResponseEntity.ok(new ApiResponse<>(true, stats, "服务器统计信息", null));
    }

    // --- 邮件群发 ---

    @PostMapping("/emails/broadcast")
    public ResponseEntity<ApiResponse<String>> broadcastEmail(@RequestBody BroadcastRequest request) {
        log.info("管理员群发邮件，主题：{}", request.getSubject());
        
        try {
            // 1. 获取所有有效用户邮箱
            List<String> recipients = userRepository.findAll().stream()
                    .filter(Account::getEnabled)
                    .map(Account::getEmail)
                    .collect(Collectors.toList());
            
            if (recipients.isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "没有可用的收件人", null));
            }
            
            // 2. 准备发送者凭证 (模拟 admin@mb.com)
            String senderEmail = "admin@mb.com";
            String token = jwtUtil.generateToken(senderEmail);
            tokenService.storeToken(token, senderEmail, jwtUtil.getExpiration());
            
            // 3. 发送邮件
            int successCount = 0;
            int failureCount = 0;
            
            for (String recipient : recipients) {
                try {
                    boolean success = emailProtocolService.sendEmail(
                            senderEmail, 
                            token, 
                            java.util.Collections.singletonList(recipient), 
                            request.getSubject(), 
                            request.getContent(),
                            "127.0.0.1", 
                            25, 
                            false
                    );
                    if (success) successCount++; else failureCount++;
                } catch (Exception e) {
                    log.error("Failed to send broadcast to " + recipient, e);
                    failureCount++;
                }
            }
            
            return ResponseEntity.ok(new ApiResponse<>(true, 
                    String.format("发送完成: 成功 %d, 失败 %d", successCount, failureCount), 
                    "群发完成", null));
            
        } catch (Exception e) {
            log.error("群发邮件失败", e);
            return ResponseEntity.status(500).body(new ApiResponse<>(false, null, "群发邮件失败: " + e.getMessage(), null));
        }
    }

    @Data
    public static class BroadcastRequest {
        private String subject;
        private String content;
    }

    @Data
    public static class BlacklistRequest {
        private Blacklist.Type type;
        private String value;
    }

    @Data
    public static class CreateUserRequest {
        private String username;
        private String email;
        private String password;
    }
}
