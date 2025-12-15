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
        log.info("获取用户资料请求");
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("无效的认证头信息");
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        false, null, "未提供认证信息", null
                ));
            }

            String token = authHeader.substring(7);
            String identifier = jwtUtil.extractUsername(token);

            Account currentUser = userService.getUserByEmail(identifier);
            if (currentUser == null) {
                log.warn("用户不存在: {}", identifier);
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        false, null, "用户不存在", null
                ));
            }

            log.info("成功获取用户资料: {}", identifier);
            ApiResponse<Account> response = new ApiResponse<>(
                    true, currentUser, "用户资料获取成功", null
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取用户资料失败", e);
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

            // 更新用户信息 (允许修改用户名和签名)
            if (updatedUser.getUsername() != null) currentUser.setUsername(updatedUser.getUsername());
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
