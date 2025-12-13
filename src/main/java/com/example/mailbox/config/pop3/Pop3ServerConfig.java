package com.example.mailbox.config.pop3;

import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * POP3服务器配置类
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
     * 处理POP3客户端连接
     */
    private class Pop3ClientHandler implements Runnable {
        private final Socket clientSocket;
        private BufferedReader in;
        private OutputStream out;
        private Pop3State state = Pop3State.AUTHORIZATION;
        private String user = null;
        private boolean authenticated = false;
        
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
            
            // 返回模拟邮件列表
            sendResponse("+OK 2 messages (3200 bytes)");
            sendResponse("1 1500");
            sendResponse("2 1700");
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
            
            // 返回模拟邮件内容
            sendResponse("+OK " + messageId + " octets");
            sendResponse("From: sender@example.com");
            sendResponse("To: " + user);
            sendResponse("Subject: Test Email");
            sendResponse("");
            sendResponse("This is a test email from the custom POP3 server.");
            sendResponse(".");
        }
        
        private void handleQuitCommand() throws IOException {
            sendResponse("+OK Logging out");
            clientSocket.close();
        }
        
        private void sendResponse(String response) throws IOException {
            out.write((response + "\r\n").getBytes());
            out.flush();
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
