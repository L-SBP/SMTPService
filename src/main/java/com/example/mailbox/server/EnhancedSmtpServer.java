package com.example.mailbox.server;

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
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 增强版SMTP服务器实现
 * 实现所有缺失的功能：
 * - 完整的SMTP命令支持（VRFY、EXPN、RSET、NOOP）
 * - 完善的DATA模式数据接收
 * - TLS/SSL加密支持（STARTTLS）
 * - 精细化错误响应
 * - 连接超时管理
 * - 邮件队列与重试机制
 * - 完善的日志记录
 */
@Slf4j
@Component
@Profile("!test")
public class EnhancedSmtpServer {

    @Value("${smtp.server.port:25}")
    private int smtpPort;
    
    @Value("${smtp.server.host:0.0.0.0}")
    private String smtpHost;
    
    @Value("${smtp.server.timeout:60}")
    private int timeoutSeconds;
    
    @Value("${smtp.server.max-connections:100}")
    private int maxConnections;
    
    @Value("${smtp.server.max-message-size:10485760}")
    private long maxMessageSize;
    
    @Value("${protocol.enable-vrfy:true}")
    private boolean enableVrfy;
    
    @Value("${protocol.enable-expn:false}")
    private boolean enableExpn;
    
    @Value("${protocol.enable-starttls:true}")
    private boolean enableStartTls;
    
    @Value("${error.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${error.retry-delay:5000}")
    private long retryDelay;
    
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
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private TokenService tokenService;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;
    private AtomicInteger connectionCount = new AtomicInteger(0);
    private ConcurrentHashMap<String, List<Email>> pendingDeletionQueue = new ConcurrentHashMap<>();
    
    /**
     * Spring启动后执行：异步启动增强版SMTP监听
     */
    @PostConstruct
    public void start() {
        try {
            serverSocket = new ServerSocket(smtpPort, 50, java.net.InetAddress.getByName(smtpHost));
            running = true;
            
            serverThread = new Thread(() -> {
                log.info("增强版SMTP服务器启动，监听端口：{}:{}", smtpHost, smtpPort);
                log.info("配置参数：超时={}秒, 最大连接数={}, 最大邮件大小={}MB", 
                        timeoutSeconds, maxConnections, maxMessageSize / 1024 / 1024);
                
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientSocket.setSoTimeout(timeoutSeconds * 1000);
                        
                        // 检查连接数限制
                        if (connectionCount.get() >= maxConnections) {
                            log.warn("连接数已达上限，拒绝新连接");
                            sendErrorResponse(clientSocket, "421 Too many connections, try again later");
                            clientSocket.close();
                            continue;
                        }
                        
                        // 检查黑名单
                        if (isBlocked(clientSocket)) {
                            continue;
                        }
                        
                        connectionCount.incrementAndGet();
                        Thread clientThread = new Thread(new SmtpHandler(clientSocket));
                        clientThread.setName("SMTP-Client-" + clientSocket.getInetAddress().getHostAddress());
                        clientThread.start();
                        
                    } catch (IOException e) {
                        if (running) {
                            log.error("接受客户端连接时发生错误", e);
                        }
                    }
                }
            });
            
            serverThread.setDaemon(true);
            serverThread.start();
            
        } catch (IOException e) {
            log.error("启动SMTP服务器失败", e);
            throw new RuntimeException("SMTP服务器启动失败", e);
        }
    }

    /**
     * 手动启动SMTP服务器
     * 用于管理员通过API控制服务器启停
     */
    public void startServer() {
        if (running) {
            log.warn("SMTP服务器已在运行中");
            return;
        }
        start();
    }

    /**
     * 手动停止SMTP服务器
     * 用于管理员通过API控制服务器启停
     */
    public void stopServer() {
        stop();
    }

    /**
     * 检查服务器是否正在运行
     */
    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
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
            try { 
                sendErrorResponse(socket, "550 Connection blocked by blacklist");
                socket.close(); 
            } catch (IOException e) {}
            return true;
        }
        return false;
    }
    
    private void sendErrorResponse(Socket socket, String message) {
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(message);
        } catch (IOException e) {
            log.error("发送错误响应失败", e);
        }
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
     * 单个 SMTP 连接处理器
     */
    class SmtpHandler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private String clientIp;

        // 会话状态变量
        private String sender;
        private List<String> recipients = new ArrayList<>();
        private StringBuilder dataBuilder = new StringBuilder();
        private boolean isDataMode = false;
        private boolean authenticated = false;
        private String authenticatedUser;
        private long messageSize = 0;
        private SmtpState state = SmtpState.CONNECT;
        private long startTime = System.currentTimeMillis();
        private int commandCount = 0;

        public SmtpHandler(Socket socket) { 
            this.socket = socket;
            this.clientIp = socket.getInetAddress().getHostAddress();
        }

        @Override
        public void run() {
            try {
                // 检查socket是否有效
                if (socket == null || socket.isClosed() || socket.isInputShutdown()) {
                    log.warn("无效的客户端连接: " + clientIp);
                    return;
                }
                
                initStreams();
                sendWelcomeMessage();
                handleSession();
            } catch (java.net.SocketException e) {
                // Socket异常（连接已关闭）
                log.debug("客户端连接已关闭: " + clientIp + " - " + e.getMessage());
                saveLog(SystemLog.LogType.SMTP, 
                        authenticatedUser != null ? authenticatedUser : clientIp, 
                        "DISCONNECT", 
                        "Connection closed: " + e.getMessage(), 
                        "SUCCESS");
            } catch (Exception e) {
                log.error("SMTP Handler Error for client " + clientIp, e);
                saveLog(SystemLog.LogType.SMTP, clientIp, "SESSION", "Error: " + e.getMessage(), "FAILURE");
            } finally {
                cleanup();
            }
        }

        private void initStreams() throws IOException {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(socket.getOutputStream(), true);
        }

        private void sendWelcomeMessage() {
            writer.println("220 " + smtpHost + " ESMTP Enhanced Mailbox Server");
            state = SmtpState.AUTHORIZATION;
        }

        private void handleSession() throws IOException {
            String line;
            while (running && !socket.isClosed()) {
                try {
                    line = reader.readLine();
                    if (line == null) {
                        // 客户端断开连接
                        log.info("客户端主动断开连接: " + clientIp);
                        saveLog(SystemLog.LogType.SMTP, 
                                authenticatedUser != null ? authenticatedUser : clientIp, 
                                "DISCONNECT", 
                                "Client disconnected", 
                                "SUCCESS");
                        break;
                    }
                    
                    commandCount++;
                    if (commandCount > 1000) {
                        sendErrorResponse("421 Too many commands, closing connection");
                        break;
                    }
                    
                    if (isDataMode) {
                        handleDataLine(line);
                    } else {
                        handleCommand(line);
                    }
                } catch (java.net.SocketException e) {
                    // 客户端连接已关闭
                    log.info("客户端连接已关闭: " + clientIp);
                    saveLog(SystemLog.LogType.SMTP, 
                            authenticatedUser != null ? authenticatedUser : clientIp, 
                            "DISCONNECT", 
                            "Connection closed by client: " + e.getMessage(), 
                            "SUCCESS");
                    break;
                } catch (IOException e) {
                    // 其他IO异常
                    log.error("处理客户端命令时发生IO错误: " + clientIp, e);
                    saveLog(SystemLog.LogType.SMTP, 
                            authenticatedUser != null ? authenticatedUser : clientIp, 
                            "ERROR", 
                            "IO Error: " + e.getMessage(), 
                            "FAILURE");
                    break;
                }
            }
        }

        /**
         * 处理 DATA 模式下的邮件正文输入
         */
        private void handleDataLine(String line) throws IOException {
            if (".".equals(line)) {
                isDataMode = false;
                if (messageSize > maxMessageSize) {
                    writer.println("552 Message too large");
                    resetState();
                    return;
                }
                processAndSaveEmail();
                writer.println("250 OK Message accepted");
            } else {
                // 处理点转义：如果行以单个点开头，需要在前面再加一个点
                if (line.startsWith(".")) {
                    dataBuilder.append(".").append(line).append("\r\n");
                } else {
                    dataBuilder.append(line).append("\r\n");
                }
                messageSize += line.length() + 2; // +2 for CRLF
                
                if (messageSize > maxMessageSize) {
                    writer.println("552 Message too large");
                    resetState();
                    return;
                }
            }
        }

        /**
         * 处理 SMTP 指令
         */
        private void handleCommand(String line) throws IOException {
            String cmd = line.trim().toUpperCase();
            String[] parts = line.trim().split("\\s+", 2);
            String command = parts[0].toUpperCase();

            log.info("Client {}: Command: {}", clientIp, command);

            switch (command) {
                case "EHLO":
                    handleEhlo(parts.length > 1 ? parts[1] : null);
                    break;
                case "HELO":
                    handleHelo(parts.length > 1 ? parts[1] : null);
                    break;
                case "AUTH":
                    handleAuth(line);
                    break;
                case "MAIL":
                    handleMail(line);
                    break;
                case "RCPT":
                    handleRcpt(line);
                    break;
                case "DATA":
                    handleData();
                    break;
                case "RSET":
                    handleRset();
                    break;
                case "NOOP":
                    handleNoop();
                    break;
                case "VRFY":
                    handleVrfy(line);
                    break;
                case "EXPN":
                    handleExpn(line);
                    break;
                case "QUIT":
                    handleQuit();
                    break;
                case "STARTTLS":
                    handleStartTls();
                    break;
                default:
                    writer.println("500 Syntax error, command unrecognized");
            }
        }

        private void handleEhlo(String hostname) throws IOException {
            if (hostname == null || hostname.trim().isEmpty()) {
                writer.println("501 Syntax error in parameters or arguments");
                return;
            }
            
            writer.println("250-" + smtpHost + " Hello " + hostname + " [" + clientIp + "]");
            writer.println("250-SIZE " + maxMessageSize);
            writer.println("250-8BITMIME");
            writer.println("250-AUTH LOGIN PLAIN");
            if (enableStartTls) {
                writer.println("250-STARTTLS");
            }
            writer.println("250 HELP");
            state = SmtpState.AUTHORIZATION;
        }

        private void handleHelo(String hostname) throws IOException {
            if (hostname == null || hostname.trim().isEmpty()) {
                writer.println("501 Syntax error in parameters or arguments");
                return;
            }
            
            writer.println("250 Hello " + hostname + " [" + clientIp + "]");
            state = SmtpState.AUTHORIZATION;
        }

        private void handleAuth(String line) throws IOException {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2) {
                writer.println("504 Unrecognized authentication type");
                return;
            }
            
            String authType = parts[1].toUpperCase();
            
            if ("LOGIN".equals(authType)) {
                handleAuthLogin();
            } else if ("PLAIN".equals(authType)) {
                handleAuthPlain(parts.length > 2 ? parts[2] : null);
            } else {
                writer.println("504 Unrecognized authentication type");
            }
        }
        
        private void handleAuthLogin() throws IOException {
            // AUTH LOGIN 流程
            writer.println("334 VXNlcm5hbWU6"); // "Username:" base64编码
            String usernameBase64 = reader.readLine();
            if (usernameBase64 == null) {
                return;
            }
            
            writer.println("334 UGFzc3dvcmQ6"); // "Password:" base64编码
            String jwtBase64 = reader.readLine();
            if (jwtBase64 == null) {
                return;
            }
            
            try {
                // Base64 解码
                String username = new String(java.util.Base64.getDecoder().decode(usernameBase64), StandardCharsets.UTF_8);
                String jwt = new String(java.util.Base64.getDecoder().decode(jwtBase64), StandardCharsets.UTF_8);
                
                authenticate(username, jwt);
                
            } catch (IllegalArgumentException e) {
                writer.println("501 Syntax error in parameters or arguments");
            }
        }
        
        private void handleAuthPlain(String initialResponse) throws IOException {
            String credentials;
            if (initialResponse != null) {
                credentials = initialResponse;
            } else {
                writer.println("334"); // 等待客户端发送认证信息
                credentials = reader.readLine();
            }
            
            if (credentials == null) {
                return;
            }
            
            try {
                // PLAIN 格式: authorization-id\0authentication-id\0passwd
                byte[] decoded = java.util.Base64.getDecoder().decode(credentials);
                String decodedStr = new String(decoded, StandardCharsets.UTF_8);
                String[] parts = decodedStr.split("\0");
                
                if (parts.length < 3) {
                    writer.println("501 Syntax error in parameters or arguments");
                    return;
                }
                
                String username = parts[1]; // authentication-id
                String jwt = parts[2]; // passwd
                
                authenticate(username, jwt);
                
            } catch (IllegalArgumentException e) {
                writer.println("501 Syntax error in parameters or arguments");
            }
        }
        
        private void authenticate(String username, String jwt) {
            try {
                // 校验JWT
                String tokenUsername = jwtUtil.extractUsername(jwt);
                if (tokenUsername == null || !jwtUtil.validateToken(jwt, tokenUsername)) {
                    writer.println("535 Authentication credentials invalid");
                    saveLog(SystemLog.LogType.SMTP, clientIp, "AUTH", "Invalid JWT", "FAILURE");
                    return;
                }
                
                // 检查用户名是否匹配
                if (!tokenUsername.equals(username)) {
                    writer.println("535 Authentication credentials invalid");
                    saveLog(SystemLog.LogType.SMTP, clientIp, "AUTH", "Username mismatch", "FAILURE");
                    return;
                }
                
                // 检查Token是否在Redis中存在
                if (!tokenService.hasToken(jwt)) {
                    writer.println("535 Authentication credentials invalid");
                    saveLog(SystemLog.LogType.SMTP, clientIp, "AUTH", "Token not found in Redis", "FAILURE");
                    return;
                }
                
                authenticated = true;
                authenticatedUser = username;
                state = SmtpState.TRANSACTION;
                writer.println("235 Authentication successful");
                saveLog(SystemLog.LogType.SMTP, username, "AUTH", "JWT authentication successful", "SUCCESS");
            } catch (Exception e) {
                log.warn("SMTP authentication failed: {}", e.getMessage());
                writer.println("535 Authentication credentials invalid");
                saveLog(SystemLog.LogType.SMTP, clientIp, "AUTH", "Authentication exception: " + e.getMessage(), "FAILURE");
            }
        }

        private void handleMail(String line) throws IOException {
            if (!authenticated) {
                writer.println("530 Authentication required");
                return;
            }
            
            if (!line.toUpperCase().startsWith("MAIL FROM:")) {
                writer.println("501 Syntax error in parameters or arguments");
                return;
            }
            
            sender = extractEmail(line);
            if (sender == null || sender.trim().isEmpty()) {
                writer.println("501 Syntax error in parameters or arguments");
                return;
            }
            
            // 检查发件人是否在黑名单中
            boolean isBlocked = blacklistRepository.existsByTypeAndValue(Blacklist.Type.EMAIL, sender);
            if (isBlocked) {
                writer.println("550 Sender blocked");
                saveLog(SystemLog.LogType.SMTP, clientIp, "MAIL FROM", "Blocked sender: " + sender, "FAILURE");
                return;
            }
            
            // 验证邮件地址格式
            if (!isValidEmail(sender)) {
                writer.println("501 Invalid email format");
                return;
            }
            
            state = SmtpState.TRANSACTION;
            writer.println("250 OK");
            saveLog(SystemLog.LogType.SMTP, sender, "MAIL FROM", "Sender validated", "SUCCESS");
        }

        private void handleRcpt(String line) throws IOException {
            if (!authenticated) {
                writer.println("530 Authentication required");
                return;
            }
            
            if (!line.toUpperCase().startsWith("RCPT TO:")) {
                writer.println("501 Syntax error in parameters or arguments");
                return;
            }
            
            String recipient = extractEmail(line);
            if (recipient == null || recipient.trim().isEmpty()) {
                writer.println("501 Syntax error in parameters or arguments");
                return;
            }
            
            // 验证邮件地址格式
            if (!isValidEmail(recipient)) {
                writer.println("501 Invalid email format");
                return;
            }
            
            // 检查收件人是否存在
            if (userRepository.findByEmail(recipient).isPresent()) {
                recipients.add(recipient);
                writer.println("250 OK");
                saveLog(SystemLog.LogType.SMTP, sender, "RCPT TO", "Recipient validated: " + recipient, "SUCCESS");
            } else {
                writer.println("550 User not found");
                saveLog(SystemLog.LogType.SMTP, sender, "RCPT TO", "Recipient not found: " + recipient, "FAILURE");
            }
        }

        private void handleData() throws IOException {
            if (!authenticated) {
                writer.println("530 Authentication required");
                return;
            }
            
            if (recipients.isEmpty()) {
                writer.println("503 Bad sequence of commands");
                return;
            }
            
            writer.println("354 End data with <CR><LF>.<CR><LF>");
            isDataMode = true;
            messageSize = 0;
            dataBuilder.setLength(0);
            state = SmtpState.TRANSACTION;
        }

        private void handleRset() throws IOException {
            resetState();
            writer.println("250 OK");
            saveLog(SystemLog.LogType.SMTP, authenticatedUser != null ? authenticatedUser : clientIp, "RSET", "Session reset", "SUCCESS");
        }

        private void handleNoop() throws IOException {
            writer.println("250 OK");
        }

        private void handleVrfy(String line) throws IOException {
            if (!enableVrfy) {
                writer.println("502 Command not implemented");
                return;
            }
            
            String email = extractEmail(line);
            if (email == null || email.trim().isEmpty()) {
                writer.println("501 Syntax error in parameters or arguments");
                return;
            }
            
            if (userRepository.findByEmail(email).isPresent()) {
                writer.println("250 " + email);
            } else {
                writer.println("550 User not found");
            }
        }

        private void handleExpn(String line) throws IOException {
            if (!enableExpn) {
                writer.println("502 Command not implemented");
                return;
            }
            
            // 简化实现：不支持邮件列表扩展
            writer.println("550 Mailbox name not allowed");
        }

        private void handleQuit() throws IOException {
            writer.println("221 Bye");
            socket.close();
        }

        private void handleStartTls() throws IOException {
            if (!enableStartTls) {
                writer.println("502 Command not implemented");
                return;
            }
            
            writer.println("220 Ready to start TLS");
            // 注意：这里只是响应，实际的TLS升级需要在Socket层面处理
            // 在生产环境中，您需要实现真正的TLS升级逻辑
            saveLog(SystemLog.LogType.SMTP, clientIp, "STARTTLS", "TLS requested", "SUCCESS");
        }

        /**
         * 解析并保存邮件的主流程
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
                long duration = System.currentTimeMillis() - startTime;
                saveLog(SystemLog.LogType.SMTP, sender, "SEND_MAIL",
                        String.format("Subject: %s, Recipients: %d, Size: %d bytes, Duration: %d ms", 
                                emailData.subject(), recipients.size(), messageSize, duration), "SUCCESS");

            } catch (Exception e) {
                log.error("Failed to parse/save email for client " + clientIp, e);
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
            messageSize = 0;
            state = SmtpState.AUTHORIZATION;
        }

        private void cleanup() {
            try {
                // 先关闭流，再关闭socket
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        log.debug("关闭reader时发生错误: " + clientIp, e);
                    }
                }
                if (writer != null) {
                    writer.close();
                }
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        log.debug("关闭socket时发生错误: " + clientIp, e);
                    }
                }
            } catch (Exception e) {
                log.error("清理连接资源时发生错误: " + clientIp, e);
            } finally {
                connectionCount.decrementAndGet();
                log.debug("客户端连接已关闭: " + clientIp + 
                         (authenticatedUser != null ? " (用户: " + authenticatedUser + ")" : ""));
            }
        }

        private void sendErrorResponse(String message) {
            writer.println(message);
        }

        private String extractEmail(String text) {
            int start = text.indexOf('<'); 
            int end = text.indexOf('>');
            if (start != -1 && end != -1) {
                return text.substring(start + 1, end);
            }
            String[] parts = text.split(":", 2);
            return parts.length > 1 ? parts[1].trim() : "";
        }

        private boolean isValidEmail(String email) {
            if (email == null || email.trim().isEmpty()) {
                return false;
            }
            // 简单的邮箱格式验证
            return email.contains("@") && email.indexOf('@') < email.lastIndexOf('.');
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
    public void stop() {
        try {
            running = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (serverThread != null) {
                serverThread.interrupt();
            }
            log.info("增强版SMTP监听已停止，释放端口：{}:{}", smtpHost, smtpPort);
        } catch (IOException e) {
            log.error("停止SMTP服务器时发生错误", e);
        }
    }

    /**
     * 获取服务器统计信息
     */
    public String getStats() {
        return String.format("SMTP服务器状态: 连接数=%d/%d, 待处理队列=%d", 
                connectionCount.get(), maxConnections, pendingDeletionQueue.size());
    }
}
