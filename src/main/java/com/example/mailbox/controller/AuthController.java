package com.example.mailbox.controller;

import com.example.mailbox.dto.AuthRequestDTO;
import com.example.mailbox.dto.RegisterRequestDTO;
import com.example.mailbox.entity.Account;
import com.example.mailbox.service.AuthService;
import com.example.mailbox.util.JwtUtil; // 1. 导入 JwtUtil
import com.example.mailbox.vo.ApiResponse; // 2. 导入 ApiResponse
import com.example.mailbox.vo.AuthResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap; // 3. 导入 HashMap
import java.util.Map;     // 4. 导入 Map

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil; // 5. 注入 JwtUtil Bean

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequestDTO request) {
        try {
            // 登录验证
            Account user = authService.login(request.getUsername(), request.getPassword());

            // 生成 Token
            String token = jwtUtil.generateToken(user.getEmail());

            // 构建返回数据 (包含 User 信息和 Token)
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("user", user);
            responseData.put("token", token);

            // 返回标准 ApiResponse
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                    true, responseData, "登录成功", null
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // 登录失败返回
            ApiResponse<String> response = new ApiResponse<>(
                    false, null, "登录失败: " + e.getMessage(), null
            );
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO request) {
        try {
            AuthResponseVO response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("注册失败: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok("已退出登录");
    }
}