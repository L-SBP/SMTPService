package com.example.mailbox.service.Impl;

import com.example.mailbox.config.InboundMailProperties;
import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Attachment;
import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.Email.FolderType;
import com.example.mailbox.entity.SystemLog;
import com.example.mailbox.repository.AttachmentRepository;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.SystemLogRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.AttachmentService;
import com.example.mailbox.service.InboundMailService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 入站邮件服务实现
 * 从中继邮箱（QQ/Gmail等）拉取邮件并分发给系统用户
 * 
 * 工作原理：
 * 1. 外部用户发邮件到中继邮箱（如 your-relay@qq.com）
 * 2. 邮件主题或正文中包含目标用户标识（如 @user.mb.com 或 [TO:user@mb.com]）
 * 3. 服务定时拉取中继邮箱的新邮件
 * 4. 解析目标用户并将邮件保存到对应用户的收件箱
 */
@Service
@Slf4j
public class InboundMailServiceImpl implements InboundMailService {

  @Autowired
  private InboundMailProperties inboundProperties;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EmailRepository emailRepository;

  @Autowired
  private AttachmentService attachmentService;

  @Autowired
  private SystemLogRepository systemLogRepository;

  // 状态跟踪
  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final AtomicLong totalFetched = new AtomicLong(0);
  private volatile long lastFetchTime = 0;
  private volatile int lastFetchCount = 0;
  private volatile String lastError = null;

  // 用于解析目标用户的正则表达式
  // 支持格式：[TO:user@mb.com] 或 直接在收件人/抄送中查找
  private static final Pattern TARGET_USER_PATTERN = Pattern.compile(
      "\\[TO:([^\\]]+@[^\\]]+)\\]|\\bto:([\\w.+-]+@[\\w.-]+)\\b",
      Pattern.CASE_INSENSITIVE);

  /**
   * 记录入站邮件日志
   */
  private void logInbound(String operator, String action, String details, boolean success) {
    try {
      SystemLog logEntry = new SystemLog();
      logEntry.setType(SystemLog.LogType.INBOUND);
      logEntry.setOperator(operator);
      logEntry.setAction(action);
      logEntry.setDetails(details);
      logEntry.setStatus(success ? "SUCCESS" : "FAILURE");
      systemLogRepository.save(logEntry);
    } catch (Exception e) {
      log.error("记录入站日志失败: {}", e.getMessage());
    }
  }

  @Override
  public boolean isEnabled() {
    return inboundProperties.isEnabled();
  }

  @Override
  public InboundConfigStatus getConfigStatus() {
    InboundConfigStatus status = new InboundConfigStatus();
    status.setEnabled(inboundProperties.isEnabled());
    status.setProtocol(inboundProperties.getProtocol());
    status.setHost(inboundProperties.getHost());
    status.setPort(inboundProperties.getPort());
    status.setSslEnabled(inboundProperties.isSsl());
    status.setSystemDomain(inboundProperties.getSystemDomain());
    status.setFetchInterval(inboundProperties.getFetchInterval());

    // 隐藏用户名部分
    if (isNotBlank(inboundProperties.getUsername())) {
      String username = inboundProperties.getUsername();
      if (username.contains("@")) {
        int atIndex = username.indexOf("@");
        status.setUsername(username.substring(0, Math.min(3, atIndex)) + "***" + username.substring(atIndex));
      } else {
        status.setUsername(username.substring(0, Math.min(3, username.length())) + "***");
      }
    }

    // 检查配置完整性
    boolean configured = inboundProperties.isEnabled()
        && isNotBlank(inboundProperties.getHost())
        && isNotBlank(inboundProperties.getUsername())
        && isNotBlank(inboundProperties.getPassword());
    status.setConfigured(configured);

    if (!configured && inboundProperties.isEnabled()) {
      StringBuilder errorMsg = new StringBuilder("入站邮件配置不完整: ");
      if (!isNotBlank(inboundProperties.getHost())) {
        errorMsg.append("缺少host; ");
      }
      if (!isNotBlank(inboundProperties.getUsername())) {
        errorMsg.append("缺少username; ");
      }
      if (!isNotBlank(inboundProperties.getPassword())) {
        errorMsg.append("缺少password; ");
      }
      status.setErrorMessage(errorMsg.toString());
    }

    return status;
  }

  @Override
  public FetchStatus getStatus() {
    FetchStatus status = new FetchStatus();
    status.setEnabled(inboundProperties.isEnabled());
    status.setRunning(isRunning.get());
    status.setLastFetchTime(lastFetchTime);
    status.setLastFetchCount(lastFetchCount);
    status.setLastError(lastError);
    status.setTotalFetched(totalFetched.get());
    return status;
  }

  @Override
  public boolean testConnection() {
    if (!inboundProperties.isEnabled()) {
      log.warn("入站邮件功能未启用");
      return false;
    }

    Store store = null;
    try {
      store = connectToMailServer();
      log.info("入站邮件服务器连接测试成功: {}:{}", inboundProperties.getHost(), inboundProperties.getPort());
      return true;
    } catch (Exception e) {
      log.error("入站邮件服务器连接测试失败: {}", e.getMessage(), e);
      return false;
    } finally {
      closeStore(store);
    }
  }

  /**
   * 定时拉取邮件
   * 根据配置的间隔执行
   */
  @Scheduled(fixedDelayString = "${inbound.mail.fetch-interval:60}000")
  public void scheduledFetch() {
    if (inboundProperties.isEnabled()) {
      fetchEmails();
    }
  }

  @Override
  @Transactional
  public int fetchEmails() {
    if (!inboundProperties.isEnabled()) {
      return 0;
    }

    // 防止并发执行
    if (!isRunning.compareAndSet(false, true)) {
      log.debug("邮件拉取正在进行中，跳过本次执行");
      return 0;
    }

    Store store = null;
    Folder folder = null;
    int fetchedCount = 0;

    try {
      store = connectToMailServer();
      folder = openInbox(store);

      // 获取未读邮件
      Message[] messages;
      if ("imap".equalsIgnoreCase(inboundProperties.getProtocol())) {
        // IMAP 支持搜索未读邮件
        messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
      } else {
        // POP3 获取所有邮件
        messages = folder.getMessages();
      }

      log.info("从中继邮箱获取到 {} 封邮件待处理", messages.length);

      // 限制每次处理的数量
      int processCount = Math.min(messages.length, inboundProperties.getMaxFetchCount());

      for (int i = 0; i < processCount; i++) {
        Message message = messages[i];
        try {
          if (processInboundEmail(message)) {
            fetchedCount++;

            // 标记为已读或删除
            if (inboundProperties.isMarkAsReadAfterFetch()) {
              message.setFlag(Flags.Flag.SEEN, true);
            }
            if (inboundProperties.isDeleteAfterFetch()) {
              message.setFlag(Flags.Flag.DELETED, true);
            }
          }
        } catch (Exception e) {
          log.error("处理邮件失败: {}", e.getMessage(), e);
        }
      }

      lastFetchTime = System.currentTimeMillis();
      lastFetchCount = fetchedCount;
      lastError = null;
      totalFetched.addAndGet(fetchedCount);

      log.info("邮件拉取完成，成功处理 {} 封邮件", fetchedCount);

      // 记录拉取成功日志
      if (fetchedCount > 0) {
        logInbound(inboundProperties.getHost(), "FETCH_EMAILS",
            String.format("从中继邮箱拉取邮件完成: 处理 %d 封, 来源=%s:%d",
                fetchedCount, inboundProperties.getHost(), inboundProperties.getPort()),
            true);
      }

    } catch (Exception e) {
      log.error("拉取邮件失败: {}", e.getMessage(), e);
      lastError = e.getMessage();
      // 记录拉取失败日志
      logInbound(inboundProperties.getHost(), "FETCH_EMAILS",
          String.format("从中继邮箱拉取邮件失败: error=%s, 来源=%s:%d",
              e.getMessage(), inboundProperties.getHost(), inboundProperties.getPort()),
          false);
    } finally {
      closeFolder(folder);
      closeStore(store);
      isRunning.set(false);
    }

    return fetchedCount;
  }

  /**
   * 处理单封入站邮件
   */
  private boolean processInboundEmail(Message message) throws Exception {
    MimeMessage mimeMessage = (MimeMessage) message;

    // 提取发件人
    String sender = extractSender(mimeMessage);
    if (sender == null) {
      log.warn("无法提取发件人地址，跳过此邮件");
      return false;
    }

    // 提取邮件内容（先提取正文用于目标用户识别）
    String body = extractBody(mimeMessage);

    // 确定目标用户（传入正文以便搜索 @mb.com 地址）
    List<Account> targetUsers = determineTargetUsers(mimeMessage, body);
    if (targetUsers.isEmpty()) {
      log.warn("无法确定目标用户，邮件来自: {}, 主题: {}", sender, mimeMessage.getSubject());

      // 如果配置了默认收件人，使用默认收件人
      if (isNotBlank(inboundProperties.getDefaultRecipient())) {
        Account defaultUser = userRepository.findByEmail(inboundProperties.getDefaultRecipient()).orElse(null);
        if (defaultUser != null) {
          targetUsers.add(defaultUser);
          log.info("使用默认收件人: {}", inboundProperties.getDefaultRecipient());
        }
      }

      if (targetUsers.isEmpty()) {
        return false;
      }
    }

    // 提取邮件内容
    String subject = mimeMessage.getSubject();
    if (subject == null)
      subject = "(无主题)";

    List<AttachmentData> attachments = extractAttachments(mimeMessage);

    // 获取接收时间（使用邮件发送时间，如果有的话）
    LocalDateTime receivedTime = mimeMessage.getSentDate() != null
        ? mimeMessage.getSentDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        : LocalDateTime.now();

    // 为每个目标用户创建邮件副本
    for (Account targetUser : targetUsers) {
      Email email = new Email();
      email.setSender(sender);
      email.setRecipients(Collections.singletonList(targetUser.getEmail()));
      email.setSubject(subject);
      email.setBody(body);
      email.setUser(targetUser);
      email.setFolderType(FolderType.INBOX);
      email.setReceivedTime(receivedTime);
      email.setHasAttachment(!attachments.isEmpty());
      email.setIsRead(false);
      email.setIsStarred(false);

      // 保存邮件
      Email savedEmail = emailRepository.save(email);

      // 保存附件
      for (AttachmentData att : attachments) {
        attachmentService.saveAttachmentFromStream(
            savedEmail,
            new ByteArrayInputStream(att.data),
            att.contentType,
            att.fileName,
            att.data.length);
      }

      log.info("邮件已分发给用户 {}: 来自 {}, 主题: {}", targetUser.getEmail(), sender, subject);

      // 记录每封邮件分发日志
      logInbound(sender, "RECEIVE_EMAIL",
          String.format("收到外部邮件: from=%s, to=%s, subject=%s", sender, targetUser.getEmail(), subject),
          true);
    }

    return true;
  }

  /**
   * 确定目标用户
   * 1. 首先检查邮件的 To/Cc 地址中是否有系统域名的用户
   * 2. 检查主题中是否有 [TO:user@domain] 格式
   * 3. 检查正文中是否有 @domain 的邮箱地址
   */
  private List<Account> determineTargetUsers(MimeMessage message, String bodyContent) throws Exception {
    Set<String> targetEmails = new HashSet<>();
    String systemDomain = inboundProperties.getSystemDomain().toLowerCase();

    // 1. 检查 To 地址
    Address[] toAddresses = message.getRecipients(Message.RecipientType.TO);
    if (toAddresses != null) {
      for (Address addr : toAddresses) {
        String email = extractEmailAddress(addr);
        if (email != null && email.toLowerCase().endsWith("@" + systemDomain)) {
          targetEmails.add(email.toLowerCase());
        }
      }
    }

    // 2. 检查 Cc 地址
    Address[] ccAddresses = message.getRecipients(Message.RecipientType.CC);
    if (ccAddresses != null) {
      for (Address addr : ccAddresses) {
        String email = extractEmailAddress(addr);
        if (email != null && email.toLowerCase().endsWith("@" + systemDomain)) {
          targetEmails.add(email.toLowerCase());
        }
      }
    }

    // 3. 检查主题中的目标标识 [TO:user@mb.com]
    String subject = message.getSubject();
    if (subject != null) {
      Matcher matcher = TARGET_USER_PATTERN.matcher(subject);
      while (matcher.find()) {
        String email = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (email != null && email.toLowerCase().endsWith("@" + systemDomain)) {
          targetEmails.add(email.toLowerCase());
        }
      }
      // 也直接搜索主题中的 @domain 邮箱
      findEmailsInText(subject, systemDomain, targetEmails);
    }

    // 4. 检查正文中的 @domain 邮箱地址
    if (bodyContent != null) {
      findEmailsInText(bodyContent, systemDomain, targetEmails);
    }

    // 5. 根据邮箱地址查找用户
    List<Account> users = new ArrayList<>();
    for (String email : targetEmails) {
      userRepository.findByEmail(email).ifPresent(users::add);
    }

    return users;
  }

  /**
   * 从文本中查找指定域名的邮箱地址
   */
  private void findEmailsInText(String text, String systemDomain, Set<String> targetEmails) {
    if (text == null)
      return;
    // 匹配邮箱地址的正则
    Pattern emailPattern = Pattern.compile(
        "([a-zA-Z0-9._%+-]+@" + Pattern.quote(systemDomain) + ")",
        Pattern.CASE_INSENSITIVE);
    Matcher matcher = emailPattern.matcher(text);
    while (matcher.find()) {
      targetEmails.add(matcher.group(1).toLowerCase());
    }
  }

  /**
   * 从 Address 对象提取邮箱地址
   */
  private String extractEmailAddress(Address address) {
    if (address instanceof InternetAddress) {
      return ((InternetAddress) address).getAddress();
    }
    return address.toString();
  }

  /**
   * 提取发件人地址
   */
  private String extractSender(MimeMessage message) throws Exception {
    Address[] fromAddresses = message.getFrom();
    if (fromAddresses != null && fromAddresses.length > 0) {
      return extractEmailAddress(fromAddresses[0]);
    }
    return null;
  }

  /**
   * 提取邮件正文
   */
  private String extractBody(Part part) throws Exception {
    if (part.isMimeType("text/plain")) {
      return (String) part.getContent();
    } else if (part.isMimeType("text/html")) {
      return (String) part.getContent();
    } else if (part.isMimeType("multipart/*")) {
      Multipart multipart = (Multipart) part.getContent();
      StringBuilder body = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart bodyPart = multipart.getBodyPart(i);
        String disposition = bodyPart.getDisposition();
        // 跳过附件
        if (disposition == null || !disposition.equalsIgnoreCase(Part.ATTACHMENT)) {
          String content = extractBody(bodyPart);
          if (content != null && !content.isEmpty()) {
            body.append(content);
          }
        }
      }
      return body.toString();
    }
    return "";
  }

  /**
   * 提取附件
   */
  private List<AttachmentData> extractAttachments(Part part) throws Exception {
    List<AttachmentData> attachments = new ArrayList<>();
    extractAttachmentsRecursive(part, attachments);
    return attachments;
  }

  private void extractAttachmentsRecursive(Part part, List<AttachmentData> attachments) throws Exception {
    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) ||
        (part.getFileName() != null && !part.getFileName().isEmpty())) {
      // 这是附件
      String fileName = part.getFileName();
      if (fileName != null) {
        fileName = MimeUtility.decodeText(fileName);
      } else {
        fileName = "attachment_" + System.currentTimeMillis();
      }
      byte[] data = part.getInputStream().readAllBytes();
      attachments.add(new AttachmentData(fileName, part.getContentType(), data));
    } else if (part.isMimeType("multipart/*")) {
      Multipart multipart = (Multipart) part.getContent();
      for (int i = 0; i < multipart.getCount(); i++) {
        extractAttachmentsRecursive(multipart.getBodyPart(i), attachments);
      }
    }
  }

  /**
   * 连接邮件服务器
   */
  private Store connectToMailServer() throws MessagingException {
    Properties props = new Properties();
    String protocol = inboundProperties.getProtocol().toLowerCase();

    if ("imap".equals(protocol)) {
      props.put("mail.imap.host", inboundProperties.getHost());
      props.put("mail.imap.port", String.valueOf(inboundProperties.getPort()));
      props.put("mail.imap.connectiontimeout", String.valueOf(inboundProperties.getConnectionTimeout()));
      props.put("mail.imap.timeout", String.valueOf(inboundProperties.getReadTimeout()));

      if (inboundProperties.isSsl()) {
        props.put("mail.imap.ssl.enable", "true");
        props.put("mail.imap.ssl.trust", "*");
      }
    } else {
      // POP3
      props.put("mail.pop3.host", inboundProperties.getHost());
      props.put("mail.pop3.port", String.valueOf(inboundProperties.getPort()));
      props.put("mail.pop3.connectiontimeout", String.valueOf(inboundProperties.getConnectionTimeout()));
      props.put("mail.pop3.timeout", String.valueOf(inboundProperties.getReadTimeout()));

      if (inboundProperties.isSsl()) {
        props.put("mail.pop3.ssl.enable", "true");
        props.put("mail.pop3.ssl.trust", "*");
      }
    }

    Session session = Session.getInstance(props);
    String storeProtocol = inboundProperties.isSsl()
        ? (protocol.equals("imap") ? "imaps" : "pop3s")
        : protocol;

    Store store = session.getStore(storeProtocol);
    store.connect(
        inboundProperties.getHost(),
        inboundProperties.getPort(),
        inboundProperties.getUsername(),
        inboundProperties.getPassword());

    return store;
  }

  /**
   * 打开收件箱
   */
  private Folder openInbox(Store store) throws MessagingException {
    Folder folder = store.getFolder("INBOX");
    folder.open(Folder.READ_WRITE);
    return folder;
  }

  private void closeFolder(Folder folder) {
    if (folder != null && folder.isOpen()) {
      try {
        folder.close(true); // expunge deleted messages
      } catch (Exception e) {
        log.debug("关闭邮箱文件夹失败: {}", e.getMessage());
      }
    }
  }

  private void closeStore(Store store) {
    if (store != null && store.isConnected()) {
      try {
        store.close();
      } catch (Exception e) {
        log.debug("关闭邮件存储失败: {}", e.getMessage());
      }
    }
  }

  private boolean isNotBlank(String str) {
    return str != null && !str.trim().isEmpty();
  }

  /**
   * 附件数据内部类
   */
  private static class AttachmentData {
    final String fileName;
    final String contentType;
    final byte[] data;

    AttachmentData(String fileName, String contentType, byte[] data) {
      this.fileName = fileName;
      this.contentType = contentType;
      this.data = data;
    }
  }
}
