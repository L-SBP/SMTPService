package com.example.mailbox.config.smtp;

import com.example.mailbox.entity.Blacklist;
import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.SystemLog;
import com.example.mailbox.repository.BlacklistRepository;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.SystemLogRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.AttachmentService;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.service.TokenService;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.DependsOn;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * SMTP服务器配置类
 * 融合了两套方案的优点：
 * - 使用server的完整MIME解析能力
 * - 使用config的JWT认证机制
 * - 支持黑名单功能
 * - 支持系统日志记录
 * 
 * 作为基础设施层，负责启动和管理SMTP端口监听
 */
@Configuration
@Profile("!test") // 测试环境不启动
@DependsOn("pop3ServerConfig") // 确保POP3服务器先启动
@Slf4j
public class SmtpServerConfig {
    
    @Value("${smtp.server.port:25}")
    private int smtpPort;
    
    @Value("${smtp.server.host:0.0.0.0}")
    private String smtpHost;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private EmailRepository emailRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AttachmentService attachmentService;
    
    @Autowired
    private BlacklistRepository blacklistRepository;
    
    @Autowired
    private SystemLogRepository systemLogRepository;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;
    private String jwtToken;
    private String sender;
    private List<String> recipients = new ArrayList<>();
    private StringBuilder dataBuilder = new StringBuilder();
    private boolean isDataMode = false;
    private boolean authenticated = false;
    private String authenticatedUser;
    
    /**
     * Spring启动后执行：异步启动SMTP监听
     * 这是基础设施层的职责，不是Control/Service层
     */
    @PostConstruct
    public void startSmtpServer() {
        try {
            // 启动自定义SMTP服务器（支持JWT认证）
            startCustomSmtpServer();
            
        } catch (Exception e) {
            log.error("SMTP监听启动失败：{}", e.getMessage(), e);
            throw new RuntimeException("SMTP服务器启动失败", e);
        }
    }
    
    /**
     * 启动自定义SMTP服务器（支持JWT认证）
     */
    private void startCustomSmtpServer() {
        try {
            serverSocket = new ServerSocket(smtpPort);
            running = true;
            
            serverThread = new Thread(() -> {
                log.info("自定义SMTP服务器启动，监听端口：{}:{}", smtpHost, smtpPort);
                
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        log.info("新SMTP客户端连接：{}", clientSocket.getInetAddress());
                        
                        // 检查黑名单
                        if (isBlocked(clientSocket)) {
                            continue;
                        }
                        
                        // 为每个客户端创建新线程处理
                        Thread clientThread = new Thread(new SmtpClientHandler(clientSocket));
                        clientThread.start();
                        
                    } catch (IOException e) {
                        if (running) {
                            log.error("接受SMTP客户端连接时发生错误", e);
                        }
                    }
                }
            });
            
            serverThread.setDaemon(true);
            serverThread.start();
            
        } catch (IOException e) {
            log.error("启动自定义SMTP服务器失败", e);
        }
    }
    
    /**
     * 检查客户端 IP 是否在黑名单中
     */
    private boolean isBlocked(Socket socket) {
        String clientIp = socket.getInetAddress().getHostAddress();
        boolean exists = blacklistRepository.existsByTypeAndValue(Blacklist.Type.IP, clientIp);

        if (exists) {
            log.warn("Blocked connection from blacklisted IP: " + clientIp);
            saveLog(SystemLog.LogType.SMTP, clientIp, "CONNECT", "Connection blocked by IP Blacklist", "FAILURE");
            try { socket.close(); } catch (IOException e) {}
            return true;
        }
        return false;
    }
    
    /**
     * 辅助方法：保存系统日志到数据库
     */
    private void saveLog(SystemLog.LogType type, String operator, String action, String details, String status) {
        try {
            SystemLog logEntry = new SystemLog();
            logEntry.setType(type);
            logEntry.setOperator(operator);
            logEntry.setAction(action);
            logEntry.setDetails(details);
            logEntry.setStatus(status);
            systemLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save system log", e);
        }
    }
    
    /**
     * 处理SMTP客户端连接
     */
    private class SmtpClientHandler implements Runnable {
        private final Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;
        private SmtpState state = SmtpState.AUTHORIZATION;
        private String username = null;
        private boolean authenticated = false;
        private String jwtToken;
        private String sender;
        private List<String> recipients = new ArrayList<>();
        private StringBuilder dataBuilder = new StringBuilder();
        private boolean isDataMode = false;
        
        public SmtpClientHandler(Socket socket) {
            this.clientSocket = socket;
        }
        
        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                
                // 发送欢迎消息
                sendResponse("220 SMTP Server Ready (Course Design)");
                
                String commandLine;
                while ((commandLine = in.readLine()) != null) {
                    log.info("收到SMTP命令：{}", commandLine);
                    handleCommand(commandLine);
                }
                
            } catch (IOException e) {
                log.error("处理SMTP客户端连接时发生错误", e);
            } finally {
                try {
                    clientSocket.close();
                    log.info("SMTP客户端连接已关闭");
                } catch (IOException e) {
                    log.error("关闭SMTP客户端连接时发生错误", e);
                }
            }
        }
        
        private void handleCommand(String commandLine) throws IOException {
            String[] parts = commandLine.split(" ", 2);
            String command = parts[0].toUpperCase();
            
            switch (command) {
                case "EHLO":
                case "HELO":
                    handleHeloCommand(parts.length > 1 ? parts[1] : null);
                    break;
                case "AUTH":
                    handleAuthCommand(commandLine);
                    break;
                case "MAIL":
                    handleMailCommand(parts.length > 1 ? parts[1] : null);
                    break;
                case "RCPT":
                    handleRcptCommand(parts.length > 1 ? parts[1] : null);
                    break;
                case "DATA":
                    handleDataCommand();
                    break;
                case "QUIT":
                    handleQuitCommand();
                    break;
                default:
                    sendResponse("500 Command not recognized");
            }
        }
        
        private void handleHeloCommand(String hostname) throws IOException {
            if (hostname == null) {
                sendResponse("501 Syntax error in parameters or arguments");
                return;
            }
            
            state = SmtpState.AUTHORIZATION;
            sendResponse("250 Hello " + hostname + ", pleased to meet you");
        }
        
        private void handleAuthCommand(String commandLine) throws IOException {
            String[] parts = commandLine.split(" ");
            if (parts.length < 2 || !"JWT".equalsIgnoreCase(parts[1])) {
                sendResponse("504 Unrecognized authentication type");
                return;
            }
            
            // 提示客户端发送JWT
            sendResponse("334 Send JWT token");
            
            // 读取客户端的JWT
            String jwt = in.readLine();
            if (jwt == null || jwt.isEmpty()) {
                sendResponse("501 Syntax error in parameters or arguments");
                return;
            }
            
            // 校验JWT
            String username = jwtUtil.extractUsername(jwt);
            if (username == null || !jwtUtil.validateToken(jwt, username)) {
                sendResponse("535 Authentication credentials invalid");
                return;
            }
            
            // 检查Token是否在Redis中存在
            if (!tokenService.hasToken(jwt)) {
                sendResponse("535 Authentication credentials invalid");
                return;
            }
            
            // 认证通过
            authenticated = true;
            this.username = username;
            this.jwtToken = jwt;
            state = SmtpState.TRANSACTION;
            sendResponse("235 Authentication successful");
        }
        
        private void handleMailCommand(String from) throws IOException {
            if (!authenticated) {
                sendResponse("530 Authentication required");
                return;
            }
            
            if (from == null || !from.startsWith("FROM:")) {
                sendResponse("501 Syntax error in parameters or arguments");
                return;
            }
            
            // 提取发件人邮箱
            sender = extractEmail(from);
            
            // 检查发件人是否在黑名单中
            boolean isBlocked = blacklistRepository.existsByTypeAndValue(Blacklist.Type.EMAIL, sender);
            if (isBlocked) {
                sendResponse("550 Sender blocked");
                saveLog(SystemLog.LogType.SMTP, clientSocket.getInetAddress().getHostAddress(), "MAIL FROM", "Blocked sender: " + sender, "FAILURE");
                return;
            }
            
            state = SmtpState.TRANSACTION;
            sendResponse("250 Ok");
        }
        
        private void handleRcptCommand(String to) throws IOException {
            if (!authenticated) {
                sendResponse("530 Authentication required");
                return;
            }
            
            if (to == null || !to.startsWith("TO:")) {
                sendResponse("501 Syntax error in parameters or arguments");
                return;
            }
            
            // 提取收件人邮箱
            String recipient = extractEmail(to);
            recipients.add(recipient);
            sendResponse("250 Ok");
        }
        
        private void handleDataCommand() throws IOException {
            if (!authenticated) {
                sendResponse("530 Authentication required");
                return;
            }
            
            if (recipients.isEmpty()) {
                sendResponse("503 Bad sequence of commands");
                return;
            }
            
            sendResponse("354 Enter message, ending with '.' on a line by itself");
            isDataMode = true;
        }
        
        private void handleQuitCommand() throws IOException {
            sendResponse("221 Bye");
            clientSocket.close();
        }
        
        private void sendResponse(String response) throws IOException {
            out.println(response);
            out.flush();
        }
        
        /**
         * 处理邮件数据
         */
        private void processAndSaveEmail() {
            if (recipients.isEmpty()) return;
            try {
                // 1. 解析邮件
                MimeMessage mimeMessage = parseMimeMessage();
                ParsedEmailData emailData = extractEmailData(mimeMessage);

                // 2. 保存并记录日志
                saveEmailToRecipients(emailData);

                // 成功日志入库
                saveLog(SystemLog.LogType.SMTP, sender, "SEND_MAIL",
                        "Subject: " + emailData.subject() + ", Recipients: " + recipients.size(), "SUCCESS");

            } catch (Exception e) {
                log.error("Failed to parse/save email", e);
                saveLog(SystemLog.LogType.SMTP, sender, "SEND_MAIL", "Failed to save email: " + e.getMessage(), "FAILURE");
            } finally {
                resetState();
            }
        }
        
        private MimeMessage parseMimeMessage() throws Exception {
            Session session = Session.getDefaultInstance(new Properties());
            return new MimeMessage(session, new ByteArrayInputStream(dataBuilder.toString().getBytes(StandardCharsets.UTF_8)));
        }

        // 内部记录类：用于在方法间传递解析后的数据
        record ParsedEmailData(String subject, String body, List<SavedAttachment> attachments) {}

        // 内部记录类：暂存附件数据
        record SavedAttachment(byte[] data, String fileName, String contentType) {}

        private ParsedEmailData extractEmailData(MimeMessage message) throws Exception {
            StringBuilder textBody = new StringBuilder();
            List<SavedAttachment> attachments = new ArrayList<>();
            // 递归解析 MIME 树
            parseMimeContent(message, textBody, attachments);
            return new ParsedEmailData(message.getSubject(), textBody.toString(), attachments);
        }

        /**
         * 递归解析 MIME 内容（区分文本和附件）
         */
        private void parseMimeContent(Part part, StringBuilder textBody, List<SavedAttachment> attachments) throws Exception {
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) ||
                    (part.getFileName() != null && !part.getFileName().isEmpty())) {
                // 发现附件
                attachments.add(new SavedAttachment(part.getInputStream().readAllBytes(), part.getFileName(), part.getContentType()));
            } else if (part.isMimeType("text/*")) {
                // 发现正文
                textBody.append((String) part.getContent()).append("\n");
            } else if (part.isMimeType("multipart/*")) {
                // 复合类型，递归处理
                Multipart multipart = (Multipart) part.getContent();
                for (int i = 0; i < multipart.getCount(); i++) {
                    parseMimeContent(multipart.getBodyPart(i), textBody, attachments);
                }
            }
        }

        private void saveEmailToRecipients(ParsedEmailData data) {
            for (String recipient : recipients) {
                // 仅当收件人是本系统用户时才保存
                userRepository.findByEmail(recipient).ifPresent(user -> {
                    Email email = new Email();
                    email.setSender(sender != null ? sender : "unknown");
                    email.setRecipients(new ArrayList<>(recipients));
                    email.setSubject(data.subject() != null ? data.subject() : "(No Subject)");
                    email.setBody(data.body());
                    email.setUser(user);
                    email.setFolderType(Email.FolderType.INBOX);
                    email.setReceivedTime(LocalDateTime.now());
                    email.setHasAttachment(!data.attachments().isEmpty());

                    // 保存邮件本体
                    Email savedEmail = emailRepository.save(email);

                    // 保存附件文件
                    saveAttachments(savedEmail, data.attachments());

                    log.info("Email saved for user: " + recipient);
                });
            }
        }

        private void saveAttachments(Email email, List<SavedAttachment> attachments) {
            for (SavedAttachment att : attachments) {
                attachmentService.saveAttachmentFromStream(
                        email,
                        new ByteArrayInputStream(att.data()),
                        att.contentType(),
                        att.fileName(),
                        att.data().length
                );
            }
        }
        
        private void resetState() {
            sender = null;
            recipients.clear();
            dataBuilder.setLength(0);
            isDataMode = false;
        }
        
        private String extractEmail(String text) {
            int start = text.indexOf('<'); int end = text.indexOf('>');
            if (start != -1 && end != -1) return text.substring(start + 1, end);
            String[] parts = text.split(":", 2);
            return parts.length > 1 ? parts[1].trim() : "";
        }
    }
    
    /**
     * SMTP状态枚举
     */
    private enum SmtpState {
        CONNECT,
        AUTHORIZATION,
        TRANSACTION,
        UPDATE
    }
    
    /**
     * Spring停止前执行：关闭SMTP监听
     * 释放端口资源
     */
    @PreDestroy
    public void stopSmtpServer() {
        try {
            running = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (serverThread != null) {
                serverThread.interrupt();
            }
            log.info("自定义SMTP监听已停止，释放端口：{}:{}", smtpHost, smtpPort);
        } catch (IOException e) {
            log.error("停止自定义SMTP服务器时发生错误", e);
        }
    }

    /**
     * 获取SMTP服务器实例（用于测试）
     */
    public ServerSocket getServerSocket() {
        return serverSocket;
    }
}
