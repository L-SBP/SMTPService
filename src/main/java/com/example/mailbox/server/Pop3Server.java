package com.example.mailbox.server;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Email;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

/**
 * 简易 POP3 服务器实现
 * 监听端口：1100 (开发环境) / 110 (生产环境)
 */
@Slf4j
@Component
public class Pop3Server {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailRepository emailRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public void start() {
        new Thread(() -> {
            int port = 1100;
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log.info("POP3 Server started on port " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new Pop3Handler(clientSocket)).start();
                }
            } catch (IOException e) {
                log.error("POP3 Server Error", e);
            }
        }).start();
    }

    class Pop3Handler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;

        // 会话状态
        private Account currentUser;
        private List<Email> messageList; // 缓存当前会话的邮件列表

        public Pop3Handler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                writer = new PrintWriter(socket.getOutputStream(), true);

                writer.println("+OK POP3 server ready");

                String line;
                String pendingUsername = null;

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+", 2);
                    String command = parts[0].toUpperCase();
                    String arg = parts.length > 1 ? parts[1] : "";

                    if ("USER".equals(command)) {
                        pendingUsername = arg;
                        writer.println("+OK User name accepted, need password");
                    }
                    else if ("PASS".equals(command)) {
                        if (authenticate(pendingUsername, arg)) {
                            currentUser = userRepository.findByEmail(pendingUsername).orElse(null);
                            // 获取该用户的收件箱邮件
                            // 注意：Spring Data JPA 分页是从0开始，这里传 null 或 unpaged 获取所有
                            messageList = emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(
                                    pendingUsername, Email.FolderType.INBOX, org.springframework.data.domain.Pageable.unpaged()
                            ).getContent();

                            writer.println("+OK Logged in successfully");
                        } else {
                            writer.println("-ERR Authentication failed");
                        }
                    }
                    else if ("STAT".equals(command)) {
                        if (!checkAuth()) continue;
                        // 返回：+OK <邮件数量> <总大小>
                        long totalSize = messageList.stream().mapToLong(e -> e.getBody().length()).sum();
                        writer.println("+OK " + messageList.size() + " " + totalSize);
                    }
                    else if ("LIST".equals(command)) {
                        if (!checkAuth()) continue;
                        writer.println("+OK Scan listing follows");
                        for (int i = 0; i < messageList.size(); i++) {
                            // POP3 序号从1开始
                            writer.println((i + 1) + " " + messageList.get(i).getBody().length());
                        }
                        writer.println(".");
                    }
                    else if ("RETR".equals(command)) {
                        if (!checkAuth()) continue;
                        try {
                            int index = Integer.parseInt(arg) - 1;
                            if (index >= 0 && index < messageList.size()) {
                                Email email = messageList.get(index);
                                writer.println("+OK " + email.getBody().length() + " octets");
                                // 发送邮件内容。如果是纯文本存储的，建议补充一些 Header 伪装成 MIME
                                sendMimeContent(email);
                                writer.println(".");
                            } else {
                                writer.println("-ERR Invalid message number");
                            }
                        } catch (NumberFormatException e) {
                            writer.println("-ERR Invalid argument");
                        }
                    }
                    else if ("QUIT".equals(command)) {
                        writer.println("+OK Bye");
                        break;
                    }
                    else {
                        writer.println("-ERR Unknown command");
                    }
                }
            } catch (Exception e) {
                log.error("POP3 Handler Error", e);
            } finally {
                try { socket.close(); } catch (IOException e) {}
            }
        }

        private boolean checkAuth() {
            if (currentUser == null) {
                writer.println("-ERR Unauthorized");
                return false;
            }
            return true;
        }

        private boolean authenticate(String username, String password) {
            if (username == null) return false;
            return userRepository.findByEmail(username)
                    .map(user -> passwordEncoder.matches(password, user.getPassword()))
                    .orElse(false);
        }

        private void sendMimeContent(Email email) {
            // 如果数据库存的是 raw data (包含 Header)，直接发：
            // writer.println(email.getBody());

            // 如果数据库存的只是正文，需要构造 Header，否则客户端可能显示为空白或乱码
            // 简单构造：
            if (!email.getBody().contains("Subject:")) {
                writer.println("Date: " + java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now()));
                writer.println("From: " + email.getSender());
                writer.println("To: " + String.join(",", email.getRecipients()));
                writer.println("Subject: " + email.getSubject());
                writer.println("Content-Type: text/plain; charset=UTF-8");
                writer.println(); // Header 和 Body 的空行
            }
            writer.println(email.getBody());
        }
    }
}