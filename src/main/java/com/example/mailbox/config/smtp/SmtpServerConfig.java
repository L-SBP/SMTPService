package com.example.mailbox.config.smtp;

import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.DependsOn;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * SMTP服务器配置类
 * 作为基础设施层，负责启动和管理SMTP端口监听
 * 
 * 注意：这是一个配置类，不是普通的Service层
 * 端口监听是持续运行的后台任务，属于基础设施层面
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

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;
    
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
     * 处理SMTP客户端连接
     */
    private class SmtpClientHandler implements Runnable {
        private final Socket clientSocket;
        private BufferedReader in;
        private OutputStream out;
        private SmtpState state = SmtpState.AUTHORIZATION;
        private String username = null;
        private boolean authenticated = false;
        
        public SmtpClientHandler(Socket socket) {
            this.clientSocket = socket;
        }
        
        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = clientSocket.getOutputStream();
                
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
            
            state = SmtpState.TRANSACTION;
            sendResponse("250 Ok");
        }
        
        private void handleDataCommand() throws IOException {
            if (!authenticated) {
                sendResponse("530 Authentication required");
                return;
            }
            
            sendResponse("354 Enter message, ending with '.' on a line by itself");
            
            // 读取邮件内容
            StringBuilder message = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(".")) {
                    break;
                }
                message.append(line).append("\n");
            }
            
            // 模拟发送邮件
            log.info("收到邮件内容：{}", message.toString());
            sendResponse("250 Ok: queued");
        }
        
        private void handleQuitCommand() throws IOException {
            sendResponse("221 Bye");
            clientSocket.close();
        }
        
        private void sendResponse(String response) throws IOException {
            out.write((response + "\r\n").getBytes());
            out.flush();
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
