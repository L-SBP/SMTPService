package com.example.mailbox.config.pop3;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Attachment;
import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.SystemLog;
import com.example.mailbox.repository.AttachmentRepository;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.SystemLogRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.service.TokenService;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * POP3服务器配置类
 * 融合了两套方案的优点：
 * - 使用server的完整MIME解析和附件处理能力
 * - 使用config的JWT认证机制
 * - 支持系统日志记录
 * 
 * 作为基础设施层，负责启动和管理POP3端口监听
 * 
 * 注意：这是一个配置类，不是普通的Service层
 * 端口监听是持续运行的后台任务，属于基础设施层面
 */
@Configuration
@Profile("!test") // 测试环境不启动
@Slf4j
public class Pop3ServerConfig {
    
    @Value("${pop3.server.port:110}")
    private int pop3Port;
    
    @Value("${pop3.server.host:0.0.0.0}")
    private String pop3Host;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private EmailRepository emailRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AttachmentRepository attachmentRepository;
    
    @Autowired
    private SystemLogRepository systemLogRepository;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;
    
    /**
     * Spring启动后执行：异步启动POP3监听
     * 这是基础设施层的职责，不是Control/Service层
     */
    @PostConstruct
    public void startPop3Server() {
        try {
            // 启动自定义POP3服务器（支持JWT认证）
            startCustomPop3Server();
            
        } catch (Exception e) {
            log.error("POP3监听启动失败：{}", e.getMessage(), e);
            throw new RuntimeException("POP3服务器启动失败", e);
        }
    }
    
    /**
     * 启动自定义POP3服务器（支持JWT认证）
     */
    private void startCustomPop3Server() {
        try {
            serverSocket = new ServerSocket(pop3Port); // 使用110端口
            running = true;
            
            serverThread = new Thread(() -> {
                log.info("自定义POP3服务器启动，监听端口：{}:{}", pop3Host, pop3Port);
                
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        log.info("新客户端连接：{}", clientSocket.getInetAddress());
                        
                        // 为每个客户端创建新线程处理
                        Thread clientThread = new Thread(new Pop3ClientHandler(clientSocket));
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
            log.error("启动自定义POP3服务器失败", e);
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
     * 处理POP3客户端连接
     */
    private class Pop3ClientHandler implements Runnable {
        private final Socket clientSocket;
        private BufferedReader in;
        private OutputStream out;
        private Pop3State state = Pop3State.AUTHORIZATION;
        private String user = null;
        private boolean authenticated = false;
        private Account currentUser;
        private List<Email> messageList = new ArrayList<>();
        private String pendingUsername;
        
        public Pop3ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }
        
        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = clientSocket.getOutputStream();
                
                // 发送欢迎消息
                sendResponse("+OK POP3 Server Ready (Course Design)");
                
                String commandLine;
                while ((commandLine = in.readLine()) != null) {
                    log.info("收到命令：{}", commandLine);
                    handleCommand(commandLine);
                }
                
            } catch (IOException e) {
                log.error("处理客户端连接时发生错误", e);
            } finally {
                try {
                    clientSocket.close();
                    log.info("客户端连接已关闭");
                } catch (IOException e) {
                    log.error("关闭客户端连接时发生错误", e);
                }
            }
        }
        
        private void handleCommand(String commandLine) throws IOException {
            String[] parts = commandLine.split(" ", 2);
            String command = parts[0].toUpperCase();
            
            switch (command) {
                case "AUTH":
                    handleAuthCommand(commandLine);
                    break;
                case "USER":
                    handleUserCommand(parts.length > 1 ? parts[1] : null);
                    break;
                case "PASS":
                    handlePassCommand(parts.length > 1 ? parts[1] : null);
                    break;
                case "LIST":
                    handleListCommand();
                    break;
                case "RETR":
                    handleRetrCommand(parts.length > 1 ? parts[1] : null);
                    break;
                case "QUIT":
                    handleQuitCommand();
                    break;
                case "STAT":
                    handleStatCommand();
                    break;
                case "UIDL":
                    handleUidlCommand(parts.length > 1 ? parts[1] : null);
                    break;
                case "NOOP":
                    sendResponse("+OK");
                    break;
                case "CAPA":
                    handleCapaCommand();
                    break;
                default:
                    sendResponse("-ERR Unknown command");
            }
        }
        
        private void handleAuthCommand(String commandLine) throws IOException {
            String[] parts = commandLine.split(" ");
            if (parts.length < 2 || !"JWT".equals(parts[1])) {
                sendResponse("-ERR Invalid AUTH command (use AUTH JWT)");
                return;
            }
            
            // 提示客户端发送JWT
            sendResponse("+OK Send JWT token");
            
            // 读取客户端的JWT
            String jwt = in.readLine();
            if (jwt == null || jwt.isEmpty()) {
                sendResponse("-ERR JWT token empty");
                return;
            }
            
            // 校验JWT
            String username = jwtUtil.extractUsername(jwt);
            if (username == null || !jwtUtil.validateToken(jwt, username)) {
                sendResponse("-ERR Invalid JWT (expired/fake)");
                return;
            }
            
            // 检查Token是否在Redis中存在
            if (!tokenService.hasToken(jwt)) {
                sendResponse("-ERR Token not found in Redis");
                return;
            }
            
            // 认证通过
            authenticated = true;
            user = username;
            state = Pop3State.TRANSACTION;
            sendResponse("+OK JWT authenticated, welcome " + username);
        }
        
        private void handleUserCommand(String username) throws IOException {
            if (username == null) {
                sendResponse("-ERR Missing username");
                return;
            }
            
            user = username;
            sendResponse("+OK User accepted");
        }
        
        private void handlePassCommand(String password) throws IOException {
            if (!authenticated) {
                sendResponse("-ERR Please authenticate with JWT first");
                return;
            }
            
            // JWT已认证，忽略密码
            sendResponse("+OK Pass accepted");
        }
        
        private void handleListCommand() throws IOException {
            if (!authenticated) {
                sendResponse("-ERR Please authenticate with JWT first");
                return;
            }
            
            // 获取用户邮件列表
            if (messageList.isEmpty()) {
                var page = emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(
                        user, Email.FolderType.INBOX, org.springframework.data.domain.Pageable.unpaged());
                messageList = page != null ? page.getContent() : new ArrayList<>();
            }
            
            // 返回邮件列表
            sendResponse("+OK " + messageList.size() + " messages");
            for (int i = 0; i < messageList.size(); i++) {
                sendResponse((i + 1) + " " + getEmailSize(messageList.get(i)));
            }
            sendResponse(".");
        }
        
        private void handleRetrCommand(String messageId) throws IOException {
            if (!authenticated) {
                sendResponse("-ERR Please authenticate with JWT first");
                return;
            }
            
            if (messageId == null) {
                sendResponse("-ERR Missing message ID");
                return;
            }
            
            int index = parseIndex(messageId);
            if (index >= 0 && index < messageList.size()) {
                Email email = messageList.get(index);
                sendResponse("+OK " + getEmailSize(email) + " octets");
                try {
                    sendMimeMessage(email);
                } catch (Exception e) {
                    log.error("Send MIME error", e);
                    sendResponse("-ERR Failed to send message");
                }
                sendResponse(".");
            } else {
                sendResponse("-ERR Invalid message ID");
            }
        }
        
        private void handleQuitCommand() throws IOException {
            sendResponse("+OK Logging out");
            clientSocket.close();
        }
        
        private void handleStatCommand() throws IOException {
            if (!authenticated) {
                sendResponse("-ERR Please authenticate with JWT first");
                return;
            }
            
            // 获取用户邮件列表
            if (messageList.isEmpty()) {
                var page = emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(
                        user, Email.FolderType.INBOX, org.springframework.data.domain.Pageable.unpaged());
                messageList = page != null ? page.getContent() : new ArrayList<>();
            }
            
            long totalSize = messageList.stream().mapToLong(e -> getEmailSize(e)).sum();
            sendResponse("+OK " + messageList.size() + " " + totalSize);
        }
        
        private void handleUidlCommand(String arg) throws IOException {
            if (!authenticated) {
                sendResponse("-ERR Please authenticate with JWT first");
                return;
            }
            
            if (messageList.isEmpty()) {
                var page = emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(
                        user, Email.FolderType.INBOX, org.springframework.data.domain.Pageable.unpaged());
                messageList = page != null ? page.getContent() : new ArrayList<>();
            }
            
            if (arg.isEmpty()) {
                sendResponse("+OK Unique-ID listing follows");
                for (int i = 0; i < messageList.size(); i++) {
                    sendResponse((i + 1) + " " + messageList.get(i).getId());
                }
                sendResponse(".");
            } else {
                int index = parseIndex(arg);
                if (index >= 0 && index < messageList.size()) {
                    sendResponse("+OK " + (index + 1) + " " + messageList.get(index).getId());
                } else {
                    sendResponse("-ERR Invalid message number");
                }
            }
        }
        
        private void handleCapaCommand() throws IOException {
            sendResponse("+OK Capability list follows");
            sendResponse("USER");
            sendResponse("UIDL");
            sendResponse(".");
        }
        
        private void sendResponse(String response) throws IOException {
            out.write((response + "\r\n").getBytes());
            out.flush();
        }
        
        private int parseIndex(String arg) {
            try { return Integer.parseInt(arg) - 1; } catch (Exception e) { return -1; }
        }
        
        private long getEmailSize(Email e) {
            return e.getBody() == null ? 0 : e.getBody().length();
        }
        
        private void sendMimeMessage(Email email) throws Exception {
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);

            // 构建 Header
            mimeMessage.setFrom(new InternetAddress(email.getSender() != null ? email.getSender() : "unknown"));
            if (email.getRecipients() != null && !email.getRecipients().isEmpty()) {
                try {
                    mimeMessage.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(String.join(",", email.getRecipients())));
                } catch (Exception e) {
                    mimeMessage.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse("undisclosed-recipients"));
                }
            }
            mimeMessage.setSubject(email.getSubject() != null ? email.getSubject() : "", "UTF-8");
            mimeMessage.setSentDate(email.getReceivedTime() != null ? java.sql.Timestamp.valueOf(email.getReceivedTime()) : new java.util.Date());

            // 构建内容
            Multipart multipart = new MimeMultipart();
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(email.getBody() != null ? email.getBody() : "", "text/html; charset=UTF-8");
            multipart.addBodyPart(textPart);

            addAttachments(email, multipart);

            mimeMessage.setContent(multipart);
            mimeMessage.saveChanges();
            mimeMessage.writeTo(clientSocket.getOutputStream());
            clientSocket.getOutputStream().flush();
        }
        
        private void addAttachments(Email email, Multipart multipart) {
            if (Boolean.TRUE.equals(email.getHasAttachment())) {
                List<Attachment> attachments = attachmentRepository.findByEmailId(email.getId());
                if (attachments != null) {
                    for (Attachment att : attachments) {
                        try {
                            MimeBodyPart attachmentPart = new MimeBodyPart();
                            FileDataSource source = new FileDataSource(att.getFilePath());
                            attachmentPart.setDataHandler(new DataHandler(source));
                            attachmentPart.setFileName(MimeUtility.encodeText(att.getFileName() != null ? att.getFileName() : "unknown"));
                            multipart.addBodyPart(attachmentPart);
                        } catch (Exception e) {
                            log.error("Attach file error: " + att.getFileName(), e);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * POP3状态枚举
     */
    private enum Pop3State {
        AUTHORIZATION,
        TRANSACTION,
        UPDATE
    }
    
    /**
     * POP3命令处理器接口
     */
    private interface Pop3CommandHandler {
        void handle(String commandLine, BufferedReader in, OutputStream out, Pop3State state) throws IOException;
    }
    
    /**
     * Spring停止前执行：关闭POP3监听
     * 释放端口资源
     */
    @PreDestroy
    public void stopPop3Server() {
        try {
            // 停止自定义POP3服务器
            running = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (serverThread != null) {
                serverThread.interrupt();
            }
            log.info("自定义POP3监听已停止，释放端口：{}:{}", pop3Host, pop3Port);
        } catch (IOException e) {
            log.error("停止自定义POP3服务器时发生错误", e);
        }
    }
    
    /**
     * 获取POP3服务器实例
     */
    public ServerSocket getPop3Server() {
        return serverSocket;
    }
}
