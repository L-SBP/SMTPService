package com.example.mailbox.service;

import com.example.mailbox.dto.AttachmentDTO;

import java.util.List;

/**
 * 外发邮件服务接口
 * 负责向外部邮箱（QQ、Gmail、163等）发送邮件
 */
public interface OutboundEmailService {

  /**
   * 发送邮件到外部邮箱
   *
   * @param senderEmail 发件人邮箱（系统内用户）
   * @param recipients  收件人列表
   * @param cc          抄送列表（可选）
   * @param bcc         密送列表（可选）
   * @param subject     邮件主题
   * @param body        邮件正文
   * @param attachments 附件列表（可选）
   * @param isHtml      是否为HTML格式
   * @return 发送结果
   */
  SendResult sendToExternal(String senderEmail, List<String> recipients, List<String> cc, List<String> bcc,
      String subject, String body, List<AttachmentDTO> attachments, boolean isHtml);

  /**
   * 简化版发送方法
   */
  default SendResult sendToExternal(String senderEmail, List<String> recipients, String subject, String body) {
    return sendToExternal(senderEmail, recipients, null, null, subject, body, null, false);
  }

  /**
   * 检查外发功能是否启用
   */
  boolean isOutboundEnabled();

  /**
   * 获取外发SMTP配置状态
   */
  OutboundConfigStatus getConfigStatus();

  /**
   * 测试外发SMTP连接
   */
  boolean testConnection();

  /**
   * 发送结果类
   */
  class SendResult {
    private boolean success;
    private String message;
    private String messageId;
    private List<String> failedRecipients;

    public SendResult(boolean success, String message) {
      this.success = success;
      this.message = message;
    }

    public SendResult(boolean success, String message, String messageId) {
      this.success = success;
      this.message = message;
      this.messageId = messageId;
    }

    public boolean isSuccess() {
      return success;
    }

    public void setSuccess(boolean success) {
      this.success = success;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public String getMessageId() {
      return messageId;
    }

    public void setMessageId(String messageId) {
      this.messageId = messageId;
    }

    public List<String> getFailedRecipients() {
      return failedRecipients;
    }

    public void setFailedRecipients(List<String> failedRecipients) {
      this.failedRecipients = failedRecipients;
    }
  }

  /**
   * 外发配置状态
   */
  class OutboundConfigStatus {
    private boolean enabled;
    private boolean configured;
    private String host;
    private int port;
    private boolean sslEnabled;
    private boolean starttlsEnabled;
    private String fromAddress;
    private String errorMessage;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isConfigured() {
      return configured;
    }

    public void setConfigured(boolean configured) {
      this.configured = configured;
    }

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public boolean isSslEnabled() {
      return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
      this.sslEnabled = sslEnabled;
    }

    public boolean isStarttlsEnabled() {
      return starttlsEnabled;
    }

    public void setStarttlsEnabled(boolean starttlsEnabled) {
      this.starttlsEnabled = starttlsEnabled;
    }

    public String getFromAddress() {
      return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
      this.fromAddress = fromAddress;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }
  }
}
