package com.example.mailbox.service.Impl;

import com.example.mailbox.config.OutboundSmtpProperties;
import com.example.mailbox.dto.AttachmentDTO;
import com.example.mailbox.entity.Attachment;
import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.Email.FolderType;
import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.SystemLog;
import com.example.mailbox.repository.AttachmentRepository;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.SystemLogRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.OutboundEmailService;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * 外发邮件服务实现
 * 通过配置的SMTP中继服务器（如QQ邮箱、Gmail等）向外部发送邮件
 */
@Service
@Slf4j
public class OutboundEmailServiceImpl implements OutboundEmailService {

  @Autowired
  private OutboundSmtpProperties outboundSmtpProperties;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EmailRepository emailRepository;

  @Autowired
  private AttachmentRepository attachmentRepository;

  @Autowired
  private SystemLogRepository systemLogRepository;

  /**
   * 记录外发邮件日志
   */
  private void logOutbound(String operator, String action, String details, boolean success) {
    try {
      SystemLog logEntry = new SystemLog();
      logEntry.setType(SystemLog.LogType.OUTBOUND);
      logEntry.setOperator(operator);
      logEntry.setAction(action);
      logEntry.setDetails(details);
      logEntry.setStatus(success ? "SUCCESS" : "FAILURE");
      systemLogRepository.save(logEntry);
    } catch (Exception e) {
      log.error("记录外发日志失败: {}", e.getMessage());
    }
  }

  @Override
  public boolean isOutboundEnabled() {
    return outboundSmtpProperties.isEnabled();
  }

  @Override
  public OutboundConfigStatus getConfigStatus() {
    OutboundConfigStatus status = new OutboundConfigStatus();
    status.setEnabled(outboundSmtpProperties.isEnabled());
    status.setHost(outboundSmtpProperties.getHost());
    status.setPort(outboundSmtpProperties.getPort());
    status.setSslEnabled(outboundSmtpProperties.isSsl());
    status.setStarttlsEnabled(outboundSmtpProperties.isStarttls());

    // 检查配置是否完整
    boolean configured = outboundSmtpProperties.isEnabled()
        && isNotBlank(outboundSmtpProperties.getHost())
        && isNotBlank(outboundSmtpProperties.getUsername())
        && isNotBlank(outboundSmtpProperties.getPassword());

    status.setConfigured(configured);

    if (isNotBlank(outboundSmtpProperties.getFromOverride())) {
      status.setFromAddress(outboundSmtpProperties.getFromOverride());
    } else {
      status.setFromAddress(outboundSmtpProperties.getUsername());
    }

    if (!configured && outboundSmtpProperties.isEnabled()) {
      StringBuilder errorMsg = new StringBuilder("外发SMTP配置不完整: ");
      if (!isNotBlank(outboundSmtpProperties.getHost())) {
        errorMsg.append("缺少host; ");
      }
      if (!isNotBlank(outboundSmtpProperties.getUsername())) {
        errorMsg.append("缺少username; ");
      }
      if (!isNotBlank(outboundSmtpProperties.getPassword())) {
        errorMsg.append("缺少password; ");
      }
      status.setErrorMessage(errorMsg.toString());
    }

    return status;
  }

  @Override
  public boolean testConnection() {
    if (!outboundSmtpProperties.isEnabled()) {
      log.warn("外发SMTP未启用，无法测试连接");
      return false;
    }

    try {
      Properties props = buildSmtpProperties();
      Session session = Session.getInstance(props, new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(
              outboundSmtpProperties.getUsername(),
              outboundSmtpProperties.getPassword());
        }
      });

      Transport transport = session.getTransport("smtp");
      transport.connect();
      transport.close();

      log.info("外发SMTP连接测试成功: {}:{}", outboundSmtpProperties.getHost(), outboundSmtpProperties.getPort());
      return true;
    } catch (Exception e) {
      log.error("外发SMTP连接测试失败: {}", e.getMessage(), e);
      return false;
    }
  }

  @Override
  @Transactional
  public SendResult sendToExternal(String senderEmail, List<String> recipients, List<String> cc, List<String> bcc,
      String subject, String body, List<AttachmentDTO> attachmentDTOs, boolean isHtml) {

    // 检查配置
    if (!outboundSmtpProperties.isEnabled()) {
      return new SendResult(false, "外发SMTP未启用，请在配置文件中设置 outbound.smtp.enabled=true");
    }

    if (!isNotBlank(outboundSmtpProperties.getHost())) {
      return new SendResult(false, "外发SMTP服务器地址未配置");
    }

    if (!isNotBlank(outboundSmtpProperties.getUsername()) || !isNotBlank(outboundSmtpProperties.getPassword())) {
      return new SendResult(false, "外发SMTP认证信息未配置");
    }

    // 验证发件人
    Account user = userRepository.findByEmail(senderEmail).orElse(null);
    if (user == null) {
      return new SendResult(false, "发件人用户不存在: " + senderEmail);
    }

    // 准备发件人地址
    String fromEmail = isNotBlank(outboundSmtpProperties.getFromOverride())
        ? outboundSmtpProperties.getFromOverride()
        : outboundSmtpProperties.getUsername();

    // 准备附件文件
    List<File> attachmentFiles = new ArrayList<>();
    List<Attachment> attachments = new ArrayList<>();

    if (attachmentDTOs != null && !attachmentDTOs.isEmpty()) {
      for (AttachmentDTO dto : attachmentDTOs) {
        File file = null;
        if (dto.getId() != null) {
          Attachment attachment = attachmentRepository.findById(dto.getId()).orElse(null);
          if (attachment != null) {
            attachments.add(attachment);
            if (attachment.getFilePath() != null) {
              file = new File(attachment.getFilePath());
            }
          }
        } else if (dto.getFilePath() != null) {
          file = new File(dto.getFilePath());
        }

        if (file != null && file.exists()) {
          attachmentFiles.add(file);
          log.debug("添加附件: {}", file.getAbsolutePath());
        }
      }
    }

    String messageId = null;
    List<String> failedRecipients = new ArrayList<>();

    try {
      // 构建SMTP配置
      Properties props = buildSmtpProperties();

      // 创建认证会话
      Session session = Session.getInstance(props, new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(
              outboundSmtpProperties.getUsername(),
              outboundSmtpProperties.getPassword());
        }
      });
      session.setDebug(log.isDebugEnabled());

      // 创建邮件消息
      MimeMessage message = new MimeMessage(session);
      messageId = UUID.randomUUID().toString() + "@" + extractDomain(fromEmail);
      message.setHeader("Message-ID", "<" + messageId + ">");

      // 设置发件人：
      // - SMTP 服务商通常要求 From 地址与认证账号一致，否则可能因 SPF/DMARC 被拒收或被重写
      // - 因此这里 From 使用中继账号地址（fromEmail），但显示名称与 Reply-To 指向系统用户邮箱（senderEmail）
      String displayName = isNotBlank(outboundSmtpProperties.getDefaultDisplayName())
          ? outboundSmtpProperties.getDefaultDisplayName()
          : user.getUsername();
      String personal = displayName + " <" + senderEmail + ">";
      message.setFrom(new InternetAddress(fromEmail, personal, "UTF-8"));
      message.setReplyTo(new Address[] { new InternetAddress(senderEmail) });
      // 额外保留原始发件人信息（便于排查/展示）
      message.setHeader("X-Original-From", senderEmail);

      // 设置收件人
      if (recipients != null && !recipients.isEmpty()) {
        for (String recipient : recipients) {
          try {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
          } catch (AddressException e) {
            log.warn("无效的收件人地址: {}", recipient);
            failedRecipients.add(recipient);
          }
        }
      }

      // 设置抄送
      if (cc != null && !cc.isEmpty()) {
        for (String ccAddr : cc) {
          try {
            message.addRecipient(Message.RecipientType.CC, new InternetAddress(ccAddr));
          } catch (AddressException e) {
            log.warn("无效的抄送地址: {}", ccAddr);
          }
        }
      }

      // 设置密送
      if (bcc != null && !bcc.isEmpty()) {
        for (String bccAddr : bcc) {
          try {
            message.addRecipient(Message.RecipientType.BCC, new InternetAddress(bccAddr));
          } catch (AddressException e) {
            log.warn("无效的密送地址: {}", bccAddr);
          }
        }
      }

      // 检查是否有有效收件人
      if (message.getAllRecipients() == null || message.getAllRecipients().length == 0) {
        return new SendResult(false, "没有有效的收件人");
      }

      // 设置主题
      message.setSubject(subject, "UTF-8");

      // 设置发送时间
      message.setSentDate(new java.util.Date());

      // 构建邮件内容
      Multipart multipart = new MimeMultipart();

      // 添加正文
      MimeBodyPart textPart = new MimeBodyPart();
      if (isHtml) {
        textPart.setContent(body, "text/html; charset=UTF-8");
      } else {
        textPart.setText(body, "UTF-8");
      }
      multipart.addBodyPart(textPart);

      // 添加附件
      for (File file : attachmentFiles) {
        MimeBodyPart attachmentPart = new MimeBodyPart();
        DataSource source = new FileDataSource(file);
        attachmentPart.setDataHandler(new DataHandler(source));
        attachmentPart.setFileName(MimeUtility.encodeText(file.getName(), "UTF-8", null));
        multipart.addBodyPart(attachmentPart);
      }

      message.setContent(multipart);

      // 发送邮件
      log.info("开始外发邮件: from={}, to={}, subject={}, host={}:{}",
          fromEmail, recipients, subject, outboundSmtpProperties.getHost(), outboundSmtpProperties.getPort());

      Transport.send(message);

      log.info("外发邮件成功: messageId={}", messageId);

      // 记录成功日志
      logOutbound(senderEmail, "SEND_EXTERNAL", 
          String.format("外发邮件成功: to=%s, subject=%s, via=%s:%d", 
              recipients, subject, outboundSmtpProperties.getHost(), outboundSmtpProperties.getPort()), 
          true);

      // 保存到已发送文件夹
      saveToSentFolder(user, senderEmail, recipients, cc, bcc, subject, body, attachmentDTOs, attachments);

      SendResult result = new SendResult(true, "邮件发送成功", messageId);
      if (!failedRecipients.isEmpty()) {
        result.setFailedRecipients(failedRecipients);
        result.setMessage("邮件发送成功，但部分收件人地址无效");
      }
      return result;

    } catch (SendFailedException e) {
      log.error("部分邮件发送失败: {}", e.getMessage());
      // 记录失败日志
      logOutbound(senderEmail, "SEND_EXTERNAL", 
          String.format("外发邮件部分失败: to=%s, subject=%s, error=%s", recipients, subject, e.getMessage()), 
          false);
      Address[] invalid = e.getInvalidAddresses();
      if (invalid != null) {
        for (Address addr : invalid) {
          failedRecipients.add(addr.toString());
        }
      }
      SendResult result = new SendResult(false, "部分邮件发送失败: " + e.getMessage());
      result.setFailedRecipients(failedRecipients);
      return result;

    } catch (AuthenticationFailedException e) {
      log.error("SMTP认证失败: {}", e.getMessage());
      logOutbound(senderEmail, "SEND_EXTERNAL", 
          String.format("SMTP认证失败: to=%s, subject=%s, error=%s", recipients, subject, e.getMessage()), 
          false);
      return new SendResult(false, "SMTP认证失败，请检查用户名和密码（授权码）");

    } catch (MessagingException e) {
      log.error("邮件发送失败: {}", e.getMessage(), e);
      logOutbound(senderEmail, "SEND_EXTERNAL", 
          String.format("邮件发送失败: to=%s, subject=%s, error=%s", recipients, subject, e.getMessage()), 
          false);
      return new SendResult(false, "邮件发送失败: " + e.getMessage());

    } catch (Exception e) {
      log.error("外发邮件异常: {}", e.getMessage(), e);
      logOutbound(senderEmail, "SEND_EXTERNAL", 
          String.format("外发邮件异常: to=%s, subject=%s, error=%s", recipients, subject, e.getMessage()), 
          false);
      return new SendResult(false, "邮件发送异常: " + e.getMessage());
    }
  }

  /**
   * 构建SMTP配置属性
   */
  private Properties buildSmtpProperties() {
    Properties props = new Properties();
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.host", outboundSmtpProperties.getHost());
    props.put("mail.smtp.port", String.valueOf(outboundSmtpProperties.getPort()));
    props.put("mail.smtp.ssl.trust", outboundSmtpProperties.getHost());

    // 连接超时设置
    props.put("mail.smtp.connectiontimeout", "30000");
    props.put("mail.smtp.timeout", "60000");
    props.put("mail.smtp.writetimeout", "60000");

    // SSL/TLS配置
    if (outboundSmtpProperties.isSsl()) {
      // 使用SSL（通常端口465）
      props.put("mail.smtp.ssl.enable", "true");
      props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
      props.put("mail.smtp.socketFactory.port", String.valueOf(outboundSmtpProperties.getPort()));
    } else if (outboundSmtpProperties.isStarttls()) {
      // 使用STARTTLS（通常端口587）
      props.put("mail.smtp.starttls.enable", "true");
      props.put("mail.smtp.starttls.required", "true");
    }

    return props;
  }

  /**
   * 保存邮件到已发送文件夹
   */
  private void saveToSentFolder(Account user, String senderEmail, List<String> recipients,
      List<String> cc, List<String> bcc, String subject, String body,
      List<AttachmentDTO> attachmentDTOs, List<Attachment> existingAttachments) {

    Email email = new Email();
    email.setSender(senderEmail);
    email.setRecipients(recipients);
    email.setCc(cc);
    email.setBcc(bcc);
    email.setSubject(subject);
    email.setBody(body);
    email.setFolderType(FolderType.SENT);
    email.setUser(user);
    email.setReceivedTime(LocalDateTime.now());
    email.setRead(true);
    email.setHasAttachment(!existingAttachments.isEmpty() ||
        (attachmentDTOs != null && !attachmentDTOs.isEmpty()));

    // 计算邮件大小
    int size = (subject != null ? subject.length() : 0) + (body != null ? body.length() : 0);
    email.setSize(size);

    Email savedEmail = emailRepository.save(email);

    // 关联已有附件
    for (Attachment attachment : existingAttachments) {
      // 创建附件副本关联到新邮件
      Attachment newAttachment = new Attachment();
      newAttachment.setEmail(savedEmail);
      newAttachment.setFileName(attachment.getFileName());
      newAttachment.setFileSize(attachment.getFileSize());
      newAttachment.setContentType(attachment.getContentType());
      newAttachment.setFilePath(attachment.getFilePath());
      attachmentRepository.save(newAttachment);
    }

    // 处理新附件
    if (attachmentDTOs != null) {
      for (AttachmentDTO dto : attachmentDTOs) {
        if (dto.getId() == null && dto.getFilePath() != null) {
          Attachment newAttachment = new Attachment();
          newAttachment.setEmail(savedEmail);
          newAttachment.setFileName(dto.getFileName());
          newAttachment.setFileSize(dto.getFileSize());
          newAttachment.setContentType(dto.getContentType());
          newAttachment.setFilePath(dto.getFilePath());
          attachmentRepository.save(newAttachment);
        }
      }
    }

    log.debug("邮件已保存到已发送文件夹: id={}", savedEmail.getId());
  }

  /**
   * 从邮箱地址提取域名
   */
  private String extractDomain(String email) {
    if (email != null && email.contains("@")) {
      return email.substring(email.indexOf("@") + 1);
    }
    return "localhost";
  }

  /**
   * 检查字符串是否非空
   */
  private boolean isNotBlank(String str) {
    return str != null && !str.trim().isEmpty();
  }
}
