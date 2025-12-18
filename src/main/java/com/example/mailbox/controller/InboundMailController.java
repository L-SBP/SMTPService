package com.example.mailbox.controller;

import com.example.mailbox.service.InboundMailService;
import com.example.mailbox.service.InboundMailService.FetchStatus;
import com.example.mailbox.service.InboundMailService.InboundConfigStatus;
import com.example.mailbox.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 入站邮件控制器
 * 管理从中继邮箱拉取邮件的功能
 */
@RestController
@RequestMapping("/api/inbound")
@Slf4j
public class InboundMailController {

  @Autowired
  private InboundMailService inboundMailService;

  @Autowired
  private JwtUtil jwtUtil;

  /**
   * 检查入站邮件功能是否启用
   */
  @GetMapping("/enabled")
  public ResponseEntity<Map<String, Boolean>> isEnabled() {
    Map<String, Boolean> response = new HashMap<>();
    response.put("enabled", inboundMailService.isEnabled());
    return ResponseEntity.ok(response);
  }

  /**
   * 获取入站邮件配置状态（需要管理员权限）
   */
  @GetMapping("/status")
  public ResponseEntity<?> getStatus(HttpServletRequest request) {
    // 验证管理员权限
    String token = extractToken(request);
    if (token == null || !jwtUtil.validateToken(token)) {
      return ResponseEntity.status(401).body(Map.of("error", "未授权访问"));
    }

    String email = jwtUtil.getEmailFromToken(token);
    Boolean isAdmin = jwtUtil.getIsAdminFromToken(token);
    if (isAdmin == null || !isAdmin) {
      return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
    }

    InboundConfigStatus configStatus = inboundMailService.getConfigStatus();
    FetchStatus fetchStatus = inboundMailService.getStatus();

    Map<String, Object> response = new HashMap<>();
    response.put("config", configStatus);
    response.put("fetch", fetchStatus);

    return ResponseEntity.ok(response);
  }

  /**
   * 手动触发邮件拉取（需要管理员权限）
   */
  @PostMapping("/fetch")
  public ResponseEntity<?> triggerFetch(HttpServletRequest request) {
    // 验证管理员权限
    String token = extractToken(request);
    if (token == null || !jwtUtil.validateToken(token)) {
      return ResponseEntity.status(401).body(Map.of("error", "未授权访问"));
    }

    Boolean isAdmin = jwtUtil.getIsAdminFromToken(token);
    if (isAdmin == null || !isAdmin) {
      return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
    }

    if (!inboundMailService.isEnabled()) {
      return ResponseEntity.badRequest().body(Map.of(
          "success", false,
          "message", "入站邮件功能未启用，请在配置文件中设置 inbound.mail.enabled=true"));
    }

    log.info("管理员触发手动拉取邮件");
    int fetchedCount = inboundMailService.fetchEmails();

    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("fetchedCount", fetchedCount);
    response.put("message", String.format("成功拉取 %d 封邮件", fetchedCount));

    return ResponseEntity.ok(response);
  }

  /**
   * 测试入站邮件服务器连接（需要管理员权限）
   */
  @PostMapping("/test-connection")
  public ResponseEntity<?> testConnection(HttpServletRequest request) {
    // 验证管理员权限
    String token = extractToken(request);
    if (token == null || !jwtUtil.validateToken(token)) {
      return ResponseEntity.status(401).body(Map.of("error", "未授权访问"));
    }

    Boolean isAdmin = jwtUtil.getIsAdminFromToken(token);
    if (isAdmin == null || !isAdmin) {
      return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
    }

    if (!inboundMailService.isEnabled()) {
      return ResponseEntity.badRequest().body(Map.of(
          "success", false,
          "message", "入站邮件功能未启用"));
    }

    log.info("测试入站邮件服务器连接");
    boolean success = inboundMailService.testConnection();

    Map<String, Object> response = new HashMap<>();
    response.put("success", success);
    response.put("message", success ? "连接成功" : "连接失败，请检查配置");

    return ResponseEntity.ok(response);
  }

  /**
   * 获取拉取状态
   */
  @GetMapping("/fetch-status")
  public ResponseEntity<?> getFetchStatus(HttpServletRequest request) {
    String token = extractToken(request);
    if (token == null || !jwtUtil.validateToken(token)) {
      return ResponseEntity.status(401).body(Map.of("error", "未授权访问"));
    }

    return ResponseEntity.ok(inboundMailService.getStatus());
  }

  private String extractToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }
}
