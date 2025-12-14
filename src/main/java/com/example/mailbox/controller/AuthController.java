package com.example.mailbox.controller;

import com.example.mailbox.dto.AuthRequestDTO;
import com.example.mailbox.dto.RegisterRequestDTO;
import com.example.mailbox.service.AuthService;
import com.example.mailbox.service.TokenService;
import com.example.mailbox.vo.LoginResponseVO;
import com.example.mailbox.vo.RegisterResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequestDTO request) {
        try {
            LoginResponseVO response = authService.login(request.getIdentifier(), request.getPassword());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("登录失败: " + e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO request) {
        try {
            RegisterResponseVO response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("注册失败: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                tokenService.deleteToken(token);
            }
            return ResponseEntity.ok("已退出登录");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("登出失败: " + e.getMessage());
        }
    }
}
