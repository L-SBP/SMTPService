package com.example.mailbox.controller;

import com.example.mailbox.dto.LoginRequestDTO;
import com.example.mailbox.dto.RegisterRequestDTO;
import com.example.mailbox.service.AuthService;
import com.example.mailbox.service.TokenService;
import com.example.mailbox.vo.LoginResponseVO;
import com.example.mailbox.vo.RegisterResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 - 处理用户登录、注册和登出
 * 使用自定义 JWT 认证，不依赖 Spring Security
 */
@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private TokenService tokenService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO request) {
        log.info("用户登录请求，用户名/邮箱: {}", request.getIdentifier());
        try {
            LoginResponseVO response = authService.login(request.getIdentifier(), request.getPassword());
            log.info("用户登录成功，用户名: {}", response.getUser().getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("用户登录失败，用户名/邮箱: {}, 原因: {}", request.getIdentifier(), e.getMessage());
            return ResponseEntity.badRequest().body("登录失败: " + e.getMessage());
        }
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO request) {
        log.info("用户注册请求，用户名: {}, 邮箱: {}", request.getUsername(), request.getEmail());
        try {
            RegisterResponseVO response = authService.register(request);
            log.info("用户注册成功， 邮箱: {}", response.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("用户注册失败，用户名: {}, 邮箱: {}, 原因: {}", 
                request.getUsername(), request.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().body("注册失败: " + e.getMessage());
        }
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        log.info("用户登出请求");
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                tokenService.deleteToken(token);
                log.info("用户登出成功");
            }
            return ResponseEntity.ok("已退出登录");
        } catch (Exception e) {
            log.warn("用户登出失败，原因: {}", e.getMessage());
            return ResponseEntity.badRequest().body("登出失败: " + e.getMessage());
        }
    }
}
