package com.example.mailbox.controller;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Blacklist;
import com.example.mailbox.entity.SystemLog;
import com.example.mailbox.dto.AttachmentDTO;
import com.example.mailbox.repository.BlacklistRepository;
import com.example.mailbox.repository.GroupMemberRepository;
import com.example.mailbox.repository.SystemLogRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.server.EnhancedPop3Server;
import com.example.mailbox.server.EnhancedSmtpServer;
import com.example.mailbox.service.EmailService;
import com.example.mailbox.vo.ApiResponse;
import com.example.mailbox.vo.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.service.TokenService;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;

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
    @Autowired private SystemLogRepository systemLogRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private EmailService emailService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TokenService tokenService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account requireAdmin(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("未提供认证信息");
        }

        String token = authHeader.substring(7);
        String identifier = jwtUtil.extractUsername(token);
        if (identifier == null || !jwtUtil.validateToken(token, identifier) || !tokenService.hasToken(token)) {
            throw new RuntimeException("认证信息无效");
        }

        Account operator = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (Boolean.FALSE.equals(operator.getEnabled())) {
            throw new RuntimeException("账号已被禁用");
        }

        if (!Boolean.TRUE.equals(operator.getIsAdmin())) {
            throw new RuntimeException("无管理员权限");
        }

        return operator;
    }

    // --- 用户管理 ---

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<Account>> createUser(HttpServletRequest httpRequest, @RequestBody CreateUserRequest request) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
        if (request == null || request.getEmail() == null || request.getEmail().trim().isEmpty()
                || request.getUsername() == null || request.getUsername().trim().isEmpty()
                || request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "用户名、邮箱、密码不能为空", null));
        }
        log.info("管理员创建用户，邮箱：{}", request.getEmail());
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "邮箱已存在", null));
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "用户名已存在", null));
        }

        Account newUser = new Account();
        newUser.setUsername(request.getUsername().trim());
        newUser.setEmail(request.getEmail().trim());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
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
    public ResponseEntity<ApiResponse<String>> deleteUser(HttpServletRequest httpRequest, @PathVariable Long id) {
        Account operator;
        try {
            operator = requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
        if (operator.getId() != null && operator.getId().equals(id)) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "不能删除自己", null));
        }
        log.info("管理员删除用户，ID：{}", id);
        if (!userRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "用户不存在", null));
        }
        userRepository.deleteById(id);
        log.info("用户删除成功，ID：{}", id);
        return ResponseEntity.ok(new ApiResponse<>(true, "用户已删除", "删除成功", null));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<String>> updateUserRole(HttpServletRequest httpRequest, @PathVariable Long id, @RequestParam Boolean isAdmin) {
        Account operator;
        try {
            operator = requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
        if (operator.getId() != null && operator.getId().equals(id) && Boolean.FALSE.equals(isAdmin)) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "不能取消自己的管理员权限", null));
        }
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
    public ResponseEntity<ApiResponse<Page<Account>>> getAllUsers(HttpServletRequest httpRequest, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
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
    public ResponseEntity<ApiResponse<String>> updateUserStatus(HttpServletRequest httpRequest, @PathVariable Long id, @RequestParam Boolean enabled) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
        log.info("管理员修改用户状态，用户ID：{}，启用状态：{}", id, enabled);
        return userRepository.findById(id).map(user -> {
            user.setEnabled(enabled);
            userRepository.save(user);
            String status = enabled ? "解封" : "封禁";
            log.info("用户状态修改成功，用户ID：{}，操作：{}", id, status);
            return ResponseEntity.ok(new ApiResponse<>(true, "用户已" + status, "操作成功", null));
        }).orElse(ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "用户不存在", null)));
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<ApiResponse<String>> resetUserPassword(HttpServletRequest httpRequest, @PathVariable Long id, @RequestBody ResetUserPasswordRequest request) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
        if (request == null || request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "新密码不能为空", null));
        }
        if (request.getConfirmPassword() != null && !request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "新密码与确认密码不一致", null));
        }
        if (request.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "新密码长度至少为6位", null));
        }
        try {
            return userRepository.findById(id).map(user -> {
                user.setPassword(passwordEncoder.encode(request.getNewPassword()));
                userRepository.save(user);
                return ResponseEntity.ok(new ApiResponse<>(true, "密码已更新", "操作成功", null));
            }).orElse(ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "用户不存在", null)));
        } catch (Exception e) {
            log.error("管理员重置用户密码失败，用户ID：{}", id, e);
            return ResponseEntity.status(500).body(new ApiResponse<>(false, null, "重置密码失败: " + e.getMessage(), null));
        }
    }

    // --- 黑名单管理 ---

    @GetMapping("/blacklist")
    public ResponseEntity<ApiResponse<List<Blacklist>>> getBlacklist(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
        log.info("管理员查询黑名单列表");
        List<Blacklist> blacklist = blacklistRepository.findAll();
        log.info("成功获取黑名单，记录数：{}", blacklist.size());
        return ResponseEntity.ok(new ApiResponse<>(true, blacklist, "获取黑名单成功", null));
    }

    @PostMapping("/blacklist")
    public ResponseEntity<ApiResponse<Blacklist>> addToBlacklist(HttpServletRequest httpRequest, @RequestBody BlacklistRequest request) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
        if (request == null || request.getType() == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "类型不能为空", null));
        }
        if (request.getValue() == null || request.getValue().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "值不能为空", null));
        }

        String value = request.getValue().trim();
        log.info("管理员添加黑名单，类型：{}，值：{}", request.getType(), value);

        if (blacklistRepository.existsByValue(value)) {
            log.warn("黑名单记录已存在，值：{}", value);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "该值已存在", null));
        }

        Blacklist blacklist = new Blacklist();
        blacklist.setType(request.getType());
        blacklist.setValue(value);

        try {
            Blacklist saved = blacklistRepository.save(blacklist);
            log.info("黑名单添加成功，ID：{}", saved.getId());
            return ResponseEntity.ok(new ApiResponse<>(true, saved, "添加成功", null));
        } catch (DataIntegrityViolationException e) {
            log.warn("黑名单添加失败（可能已存在），类型：{}，值：{}", request.getType(), value);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "添加失败：该值已存在", null));
        }
    }

    @DeleteMapping("/blacklist/{id}")
    public ResponseEntity<ApiResponse<String>> removeFromBlacklist(HttpServletRequest httpRequest, @PathVariable Long id) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
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
    public ResponseEntity<ApiResponse<String>> getServerStatus(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
        String smtpStatus = smtpServer.isRunning() ? "SMTP服务器运行中" : "SMTP服务器已停止";
        String pop3Status = pop3Server.isRunning() ? "POP3服务器运行中" : "POP3服务器已停止";
        String status = smtpStatus + "，" + pop3Status;
        return ResponseEntity.ok(new ApiResponse<>(true, status, "服务器状态正常", null));
    }

    @PostMapping("/server/smtp/start")
    public ResponseEntity<ApiResponse<String>> startSmtpServer(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
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
    public ResponseEntity<ApiResponse<String>> stopSmtpServer(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
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
    public ResponseEntity<ApiResponse<String>> startPop3Server(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
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
    public ResponseEntity<ApiResponse<String>> stopPop3Server(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
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
    public ResponseEntity<ApiResponse<String>> getServerStats(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
        String smtpStats = smtpServer.getStats();
        String pop3Stats = pop3Server.getStats();
        String stats = smtpStats + "；" + pop3Stats;
        return ResponseEntity.ok(new ApiResponse<>(true, stats, "服务器统计信息", null));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<SystemLog>>> getLogs(HttpServletRequest httpRequest,
            @RequestParam(defaultValue = "200") int limit) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }

        int safeLimit = Math.max(1, Math.min(1000, limit));
        var page = systemLogRepository.findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "id")));
        return ResponseEntity.ok(new ApiResponse<>(true, page.getContent(), "获取日志成功", null));
    }

    @DeleteMapping("/logs")
    public ResponseEntity<ApiResponse<String>> clearLogs(HttpServletRequest httpRequest) {
        try {
            requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }

        systemLogRepository.deleteAll();
        return ResponseEntity.ok(new ApiResponse<>(true, "日志已清空", "清空成功", null));
    }

    // --- 邮件群发 ---

    @PostMapping("/emails/broadcast")
    public ResponseEntity<ApiResponse<String>> broadcastEmail(HttpServletRequest httpRequest, @RequestBody BroadcastRequest request) {
        final Account operator;
        try {
            operator = requireAdmin(httpRequest);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse<>(false, null, e.getMessage(), null));
        }

        String content = request.getContent() != null ? request.getContent() : request.getBody();
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "邮件主题不能为空", null));
        }
        if (content == null) {
            content = "";
        }

        log.info("管理员群发邮件，主题：{}", request.getSubject());

        try {
            String senderEmail = operator.getEmail();

            List<String> recipients;
            if (request.getGroupId() != null) {
                org.springframework.data.domain.Page<com.example.mailbox.entity.GroupMember> page =
                        groupMemberRepository.findByGroupId(request.getGroupId(), Pageable.unpaged());
                List<Long> accountIds = page.getContent().stream()
                        .map(com.example.mailbox.entity.GroupMember::getAccountId)
                        .collect(Collectors.toList());

                if (accountIds.isEmpty()) {
                    return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "群组暂无成员", null));
                }

                recipients = userRepository.findAllById(accountIds).stream()
                        .filter(Account::isEnabled)
                        .map(Account::getEmail)
                        .collect(Collectors.toList());
            } else {
                recipients = userRepository.findAll().stream()
                        .filter(Account::isEnabled)
                        .map(Account::getEmail)
                        .collect(Collectors.toList());
            }

            if (recipients.isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "没有可用的收件人", null));
            }

            Set<String> uniqueRecipients = new HashSet<>();
            for (String r : recipients) {
                if (r == null) continue;
                if (senderEmail != null && r.equalsIgnoreCase(senderEmail)) continue;
                uniqueRecipients.add(r);
            }

            if (uniqueRecipients.isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "没有可用的收件人", null));
            }

            List<String> to = new ArrayList<>(uniqueRecipients);
            emailService.sendEmail(senderEmail, to, request.getSubject(), content, request.getAttachments());

            return ResponseEntity.ok(new ApiResponse<>(true,
                    String.format("发送完成: 收件人 %d", to.size()),
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
        private String body;
        private List<AttachmentDTO> attachments;
        private Long groupId;
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

    @Data
    public static class ResetUserPasswordRequest {
        private String newPassword;
        private String confirmPassword;
    }
}
