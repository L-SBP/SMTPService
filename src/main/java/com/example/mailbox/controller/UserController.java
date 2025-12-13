package com.example.mailbox.controller;

import com.example.mailbox.entity.Account;
import com.example.mailbox.service.UserService;
import com.example.mailbox.vo.ApiResponse;
import lombok.AllArgsConstructor;
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

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Account>> getProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Account user = userService.getUserByEmail(email);
        ApiResponse<Account> response = new ApiResponse<>(
            true, user, "获取用户资料成功", null
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Account>> updateProfile(@RequestBody Account updatedUser) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Account currentUser = userService.getUserByEmail(email);

        // 更新用户信息
        currentUser.setUsername(updatedUser.getUsername());
        currentUser.setEmail(updatedUser.getEmail());
        currentUser.setSignature(updatedUser.getSignature());
        currentUser.setIsAdmin(updatedUser.getIsAdmin());

        // 保存更新
        Account savedUser = userService.save(currentUser);

        ApiResponse<Account> response = new ApiResponse<>(
            true, savedUser, "用户资料更新成功", null
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<String>> changePassword(@RequestBody PasswordChangeRequest request) {
        // 实现密码修改逻辑
        // 这里需要添加密码验证和更新逻辑

        ApiResponse<String> response = new ApiResponse<>(
            true, "密码修改成功", "密码修改成功", null
        );

        return ResponseEntity.ok(response);
    }

    // 密码修改请求DTO
    @AllArgsConstructor
    public static class PasswordChangeRequest {
        private String oldPassword;
        private String newPassword;
        private String confirmPassword;
    }
}
