package com.example.mailbox.controller;

import com.example.mailbox.dto.EmailRequestDTO;
import com.example.mailbox.service.OutboundEmailService;
import com.example.mailbox.service.OutboundEmailService.OutboundConfigStatus;
import com.example.mailbox.service.OutboundEmailService.SendResult;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.vo.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 外发邮件控制器
 * 专门处理向外部邮箱（QQ、Gmail、163等）发送邮件的请求
 */
@RestController
@RequestMapping("/api/outbound")
@Slf4j
public class OutboundEmailController {

  @Autowired
  private OutboundEmailService outboundEmailService;

  @Autowired
  private JwtUtil jwtUtil;

  /**
   * 从请求头获取用户邮箱
   */
  private String getEmailFromToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new RuntimeException("未提供认证信息");
    }
    String token = authHeader.substring(7);
    return jwtUtil.extractUsername(token);
  }

  /**
   * 发送邮件到外部邮箱
   * POST /api/outbound/send
   */
  @PostMapping("/send")
  public ResponseEntity<ApiResponse<Map<String, Object>>> sendEmail(
      HttpServletRequest request,
      @RequestBody @jakarta.validation.Valid EmailRequestDTO emailRequest) {
    try {
      String senderEmail = getEmailFromToken(request);
      log.info("接收到外发邮件请求: sender={}, to={}, subject={}",
          senderEmail, emailRequest.getTo(), emailRequest.getSubject());

      // 检查是否启用外发
      if (!outboundEmailService.isOutboundEnabled()) {
        return ResponseEntity.badRequest().body(
            new ApiResponse<>(false, null, "外发邮件功能未启用，请联系管理员配置SMTP中继", null));
      }

      // 发送邮件
      SendResult result = outboundEmailService.sendToExternal(
          senderEmail,
          emailRequest.getTo(),
          emailRequest.getCc(),
          emailRequest.getBcc(),
          emailRequest.getSubject(),
          emailRequest.getBody(),
          emailRequest.getAttachments(),
          false // 默认纯文本格式
      );

      Map<String, Object> data = new HashMap<>();
      data.put("success", result.isSuccess());
      data.put("messageId", result.getMessageId());
      if (result.getFailedRecipients() != null && !result.getFailedRecipients().isEmpty()) {
        data.put("failedRecipients", result.getFailedRecipients());
      }

      if (result.isSuccess()) {
        return ResponseEntity.ok(new ApiResponse<>(true, data, result.getMessage(), null));
      } else {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, data, result.getMessage(), null));
      }

    } catch (Exception e) {
      log.error("外发邮件失败", e);
      return ResponseEntity.badRequest().body(
          new ApiResponse<>(false, null, "外发邮件失败: " + e.getMessage(), null));
    }
  }

  /**
   * 发送HTML格式邮件到外部邮箱
   * POST /api/outbound/send-html
   */
  @PostMapping("/send-html")
  public ResponseEntity<ApiResponse<Map<String, Object>>> sendHtmlEmail(
      HttpServletRequest request,
      @RequestBody @jakarta.validation.Valid EmailRequestDTO emailRequest) {
    try {
      String senderEmail = getEmailFromToken(request);
      log.info("接收到HTML外发邮件请求: sender={}, to={}, subject={}",
          senderEmail, emailRequest.getTo(), emailRequest.getSubject());

      if (!outboundEmailService.isOutboundEnabled()) {
        return ResponseEntity.badRequest().body(
            new ApiResponse<>(false, null, "外发邮件功能未启用", null));
      }

      SendResult result = outboundEmailService.sendToExternal(
          senderEmail,
          emailRequest.getTo(),
          emailRequest.getCc(),
          emailRequest.getBcc(),
          emailRequest.getSubject(),
          emailRequest.getBody(),
          emailRequest.getAttachments(),
          true // HTML格式
      );

      Map<String, Object> data = new HashMap<>();
      data.put("success", result.isSuccess());
      data.put("messageId", result.getMessageId());

      if (result.isSuccess()) {
        return ResponseEntity.ok(new ApiResponse<>(true, data, result.getMessage(), null));
      } else {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, data, result.getMessage(), null));
      }

    } catch (Exception e) {
      log.error("外发HTML邮件失败", e);
      return ResponseEntity.badRequest().body(
          new ApiResponse<>(false, null, "外发邮件失败: " + e.getMessage(), null));
    }
  }

  /**
   * 获取外发邮件配置状态
   * GET /api/outbound/status
   */
  @GetMapping("/status")
  public ResponseEntity<ApiResponse<OutboundConfigStatus>> getStatus(HttpServletRequest request) {
    try {
      // 验证用户已登录
      getEmailFromToken(request);

      OutboundConfigStatus status = outboundEmailService.getConfigStatus();
      return ResponseEntity.ok(new ApiResponse<>(true, status, "获取配置状态成功", null));
    } catch (Exception e) {
      log.error("获取外发配置状态失败", e);
      return ResponseEntity.badRequest().body(
          new ApiResponse<>(false, null, e.getMessage(), null));
    }
  }

  /**
   * 测试外发SMTP连接（仅管理员）
   * POST /api/outbound/test-connection
   */
  @PostMapping("/test-connection")
  public ResponseEntity<ApiResponse<Map<String, Object>>> testConnection(HttpServletRequest request) {
    try {
      String email = getEmailFromToken(request);
      log.info("用户 {} 请求测试外发SMTP连接", email);

      if (!outboundEmailService.isOutboundEnabled()) {
        return ResponseEntity.badRequest().body(
            new ApiResponse<>(false, null, "外发SMTP未启用", null));
      }

      boolean success = outboundEmailService.testConnection();

      Map<String, Object> data = new HashMap<>();
      data.put("connected", success);
      data.put("config", outboundEmailService.getConfigStatus());

      if (success) {
        return ResponseEntity.ok(new ApiResponse<>(true, data, "SMTP连接测试成功", null));
      } else {
        return ResponseEntity.badRequest().body(
            new ApiResponse<>(false, data, "SMTP连接测试失败，请检查配置", null));
      }

    } catch (Exception e) {
      log.error("SMTP连接测试失败", e);
      return ResponseEntity.badRequest().body(
          new ApiResponse<>(false, null, "测试失败: " + e.getMessage(), null));
    }
  }

  /**
   * 检查外发功能是否可用
   * GET /api/outbound/enabled
   */
  @GetMapping("/enabled")
  public ResponseEntity<ApiResponse<Boolean>> isEnabled() {
    boolean enabled = outboundEmailService.isOutboundEnabled();
    return ResponseEntity.ok(new ApiResponse<>(true, enabled,
        enabled ? "外发功能已启用" : "外发功能未启用", null));
  }
}
