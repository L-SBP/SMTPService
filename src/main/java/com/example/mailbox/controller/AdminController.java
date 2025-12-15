package com.example.mailbox.controller;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Blacklist;
import com.example.mailbox.repository.BlacklistRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.vo.ApiResponse;
import com.example.mailbox.vo.Page;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')") // 仅管理员可访问
@Slf4j
public class AdminController {

    @Autowired private UserRepository userRepository;
    @Autowired private BlacklistRepository blacklistRepository;

    // --- 用户管理 ---

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
    private com.example.mailbox.server.EnhancedSmtpServer smtpServerConfig;
    
    @Autowired
    private com.example.mailbox.server.EnhancedPop3Server pop3ServerConfig;
    
    @Autowired
    private com.example.mailbox.service.EmailProtocolService emailProtocolService;
    
    @GetMapping("/server/status")
    public ResponseEntity<ApiResponse<String>> getServerStatus() {
        String smtpStatus = smtpServerConfig.isRunning() ? "SMTP服务器运行中" : "SMTP服务器已停止";
        String pop3Status = pop3ServerConfig.isRunning() ? "POP3服务器运行中" : "POP3服务器已停止";
        String status = smtpStatus + "，" + pop3Status;
        return ResponseEntity.ok(new ApiResponse<>(true, status, "服务器状态正常", null));
    }

    @PostMapping("/server/smtp/start")
    public ResponseEntity<ApiResponse<String>> startSmtpServer() {
        try {
            if (smtpServerConfig.isRunning()) {
                log.warn("SMTP服务器已在运行中，无需重复启动");
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, null, "SMTP服务器已在运行中", null)
                );
            }
            
            smtpServerConfig.start();
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
            if (!smtpServerConfig.isRunning()) {
                log.warn("SMTP服务器未在运行，无需停止");
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, null, "SMTP服务器未在运行", null)
                );
            }
            
            smtpServerConfig.stop();
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
            if (pop3ServerConfig.isRunning()) {
                log.warn("POP3服务器已在运行中，无需重复启动");
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, null, "POP3服务器已在运行中", null)
                );
            }
            
            pop3ServerConfig.start();
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
            if (!pop3ServerConfig.isRunning()) {
                log.warn("POP3服务器未在运行，无需停止");
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, null, "POP3服务器未在运行", null)
                );
            }
            
            pop3ServerConfig.stop();
            log.info("POP3服务器停止成功");
            return ResponseEntity.ok(new ApiResponse<>(true, "POP3服务器停止成功", "服务器已停止", null));
        } catch (Exception e) {
            log.error("停止POP3服务器失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "停止POP3服务器失败: " + e.getMessage(), null));
        }
    }

    @GetMapping("/server/stats")
    public ResponseEntity<ApiResponse<String>> getServerStats() {
        String smtpStats = smtpServerConfig.getStats();
        String pop3Stats = pop3ServerConfig.getStats();
        String stats = smtpStats + "；" + pop3Stats;
        return ResponseEntity.ok(new ApiResponse<>(true, stats, "服务器统计信息", null));
    }

    @Data
    public static class BlacklistRequest {
        private Blacklist.Type type;
        private String value;
    }
}
