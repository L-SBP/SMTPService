package com.example.mailbox.controller;

import com.example.mailbox.entity.Account;
import com.example.mailbox.service.UserService;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.vo.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器 - 处理用户个人资料和密码修改
 * 使用自定义 JWT 认证，不依赖 Spring Security
 */
@RestController
@RequestMapping("/api/users")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Account>> getProfile(HttpServletRequest request) {
        log.info("=== 获取用户资料请求开始 ===");
        log.info("请求方法: GET");
        log.info("请求路径: {}", request.getRequestURI());
        log.info("请求参数: {}", request.getQueryString());
        log.info("请求头信息:");
        
        // 打印所有请求头
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            String headerValue = request.getHeader(headerName);
            log.info("  {}: {}", headerName, headerValue);
        });
        
        try {
            String authHeader = request.getHeader("Authorization");
            log.info("Authorization头值: {}", authHeader);
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("无效的认证头信息 - authHeader: {}", authHeader);
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        false, null, "未提供认证信息", null
                ));
            }

            String token = authHeader.substring(7);
            log.info("提取的Token: {}", token);
            
            String identifier = jwtUtil.extractUsername(token);
            log.info("从Token提取的用户名/邮箱: {}", identifier);

            Account currentUser = userService.getUserByEmail(identifier);
            if (currentUser == null) {
                log.warn("用户不存在: {}", identifier);
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        false, null, "用户不存在", null
                ));
            }

            log.info("成功获取用户资料: {}", identifier);
            log.info("用户信息: id={}, username={}, email={}, isAdmin={}", 
                    currentUser.getId(), currentUser.getUsername(), 
                    currentUser.getEmail(), currentUser.getIsAdmin());
            
            ApiResponse<Account> response = new ApiResponse<>(
                    true, currentUser, "用户资料获取成功", null
            );

            log.info("=== 获取用户资料请求结束 - 成功 ===");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取用户资料失败", e);
            log.error("异常详细信息: {}", e.getMessage());
            log.error("异常堆栈: ", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                    false, null, "获取用户资料失败: " + e.getMessage(), null
            ));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Account>> updateProfile(@RequestBody Account updatedUser, HttpServletRequest request) {
        log.info("更新用户资料请求");
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("无效的认证头信息");
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        false, null, "未提供认证信息", null
                ));
            }

            String token = authHeader.substring(7);
            String email = jwtUtil.extractUsername(token);

            Account currentUser = userService.getUserByEmail(email);
            if (currentUser == null) {
                log.warn("用户不存在: {}", email);
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        false, null, "用户不存在", null
                ));
            }

            // 更新用户信息 (仅允许修改签名)
            if (updatedUser.getSignature() != null) currentUser.setSignature(updatedUser.getSignature());
            // 这里的 email 和 isAdmin 通常不允许普通接口随意修改，视业务而定

            Account savedUser = userService.save(currentUser);
            log.info("用户资料更新成功: {}", email);

            ApiResponse<Account> response = new ApiResponse<>(
                    true, savedUser, "用户资料更新成功", null
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新用户资料失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                    false, null, "更新用户资料失败: " + e.getMessage(), null
            ));
        }
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<String>> changePassword(@RequestBody PasswordChangeRequest request, HttpServletRequest httpRequest) {
        log.info("修改密码请求");
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("无效的认证头信息");
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        false, null, "未提供认证信息", null
                ));
            }

            String token = authHeader.substring(7);
            String email = jwtUtil.extractUsername(token);

            // 1. 验证两次输入的新密码是否一致
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                log.warn("新密码与确认密码不一致");
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        false, null, "新密码与确认密码不一致", null
                ));
            }

            // 2. 调用 Service 修改密码
            userService.changePassword(email, request.getOldPassword(), request.getNewPassword());
            log.info("密码修改成功: {}", email);

            return ResponseEntity.ok(new ApiResponse<>(
                    true, "密码修改成功", "密码修改成功", null
            ));
        } catch (Exception e) {
            log.error("修改密码失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                    false, null, "修改失败: " + e.getMessage(), null
            ));
        }
    }

    /**
     * 标记邮件为星标/取消星标
     */
    @PutMapping("/emails/{emailId}/star")
    public ResponseEntity<ApiResponse<String>> toggleStarred(@PathVariable Long emailId, 
                                                           @RequestBody StarRequest request,
                                                           HttpServletRequest httpRequest) {
        log.info("标记邮件星标请求: emailId={}, isStarred={}", emailId, request.getIsStarred());
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("无效的认证头信息");
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        false, null, "未提供认证信息", null
                ));
            }

            String token = authHeader.substring(7);
            String email = jwtUtil.extractUsername(token);

            // 调用 Service 标记星标
            userService.markAsStarred(emailId, email, request.getIsStarred());
            log.info("邮件星标标记成功: emailId={}, email={}, isStarred={}", 
                    emailId, email, request.getIsStarred());

            String message = request.getIsStarred() ? "邮件已标记为星标" : "邮件已取消星标";
            return ResponseEntity.ok(new ApiResponse<>(
                    true, message, message, null
            ));
        } catch (Exception e) {
            log.error("标记邮件星标失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                    false, null, "标记失败: " + e.getMessage(), null
            ));
        }
    }

    // 星标请求DTO
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StarRequest {
        private Boolean isStarred;
    }

    // 密码修改请求DTO
    @Data // <--- 关键：添加 @Data 生成 Getters
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PasswordChangeRequest {
        private String oldPassword;
        private String newPassword;
        private String confirmPassword;
    }
}
