package com.example.mailbox.controller;

import com.example.mailbox.entity.Account;
import com.example.mailbox.service.UserService;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.vo.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data; // 导入 Data
import lombok.NoArgsConstructor; // 导入 NoArgsConstructor
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Account>> getProfile(HttpServletRequest request) {

        String identifier = jwtUtil.extractUsername(request.getHeader("Authorization"));

        Account currentUser = userService.getUserByEmail(identifier);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Account>> updateProfile(@RequestBody Account updatedUser) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Account currentUser = userService.getUserByEmail(email);

        // 更新用户信息 (允许修改用户名和签名)
        if (updatedUser.getUsername() != null) currentUser.setUsername(updatedUser.getUsername());
        if (updatedUser.getSignature() != null) currentUser.setSignature(updatedUser.getSignature());
        // 这里的 email 和 isAdmin 通常不允许普通接口随意修改，视业务而定

        Account savedUser = userService.save(currentUser);

        ApiResponse<Account> response = new ApiResponse<>(
                true, savedUser, "用户资料更新成功", null
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<String>> changePassword(@RequestBody PasswordChangeRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        // 1. 验证两次输入的新密码是否一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                    false, null, "新密码与确认密码不一致", null
            ));
        }

        try {
            // 2. 调用 Service 修改密码
            userService.changePassword(email, request.getOldPassword(), request.getNewPassword());

            return ResponseEntity.ok(new ApiResponse<>(
                    true, "密码修改成功", "密码修改成功", null
            ));
        } catch (Exception e) {
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