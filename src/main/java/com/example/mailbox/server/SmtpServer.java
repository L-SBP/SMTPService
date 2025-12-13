package com.example.mailbox.server;

import com.example.mailbox.entity.Email;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
@Component
public class SmtpServer {

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private UserRepository userRepository;

    public void start() {
        new Thread(() -> {
            int port = 2500; // 开发环境端口
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log.info("SMTP Server started on port " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new SmtpHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                log.error("SMTP Server Error", e);
            }
        }).start();
    }

    class SmtpHandler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;

        private String sender;
        private List<String> recipients = new ArrayList<>();
        private StringBuilder dataBuilder = new StringBuilder();
        private boolean isDataMode = false;

        public SmtpHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new PrintWriter(socket.getOutputStream(), true);

                writer.println("220 Welcome to Java Mailbox SMTP Server");

                String line;
                while ((line = reader.readLine()) != null) {
                    if (isDataMode) {
                        if (".".equals(line)) {
                            isDataMode = false;
                            processAndSaveEmail(); // 核心修改：解析并保存
                            writer.println("250 OK Message accepted");
                        } else {
                            dataBuilder.append(line).append("\r\n"); // SMTP 使用 CRLF
                        }
                        continue;
                    }

                    String cmd = line.trim().toUpperCase();
                    if (cmd.startsWith("HELO") || cmd.startsWith("EHLO")) {
                        writer.println("250 Hello " + socket.getInetAddress().getHostAddress());
                    } else if (cmd.startsWith("MAIL FROM:")) {
                        sender = extractEmail(line);
                        writer.println("250 OK");
                    } else if (cmd.startsWith("RCPT TO:")) {
                        recipients.add(extractEmail(line));
                        writer.println("250 OK");
                    } else if (cmd.startsWith("DATA")) {
                        writer.println("354 End data with <CR><LF>.<CR><LF>");
                        isDataMode = true;
                    } else if (cmd.startsWith("QUIT")) {
                        writer.println("221 Bye");
                        break;
                    } else if (cmd.equals("RSET")) {
                        resetState();
                        writer.println("250 OK");
                    } else {
                        writer.println("500 Unknown command");
                    }
                }
            } catch (Exception e) {
                log.error("SMTP Handler Error", e);
            } finally {
                try { socket.close(); } catch (IOException e) {}
            }
        }

        private void processAndSaveEmail() {
            if (recipients.isEmpty()) return;

            try {
                // 使用 JavaMail 解析 MIME 内容
                Session session = Session.getDefaultInstance(new Properties());
                MimeMessage mimeMessage = new MimeMessage(session,
                        new ByteArrayInputStream(dataBuilder.toString().getBytes(StandardCharsets.UTF_8)));

                String subject = mimeMessage.getSubject();
                String body = getTextFromMimeMessage(mimeMessage); // 解析正文

                // 投递给所有有效的本地接收者
                for (String recipient : recipients) {
                    userRepository.findByEmail(recipient).ifPresent(user -> {
                        Email email = new Email();
                        email.setSender(sender != null ? sender : "unknown");
                        email.setRecipients(new ArrayList<>(recipients));
                        email.setSubject(subject != null ? subject : "(No Subject)");
                        email.setBody(body);
                        email.setUser(user);
                        email.setFolderType(Email.FolderType.INBOX);
                        email.setReceivedTime(LocalDateTime.now());

                        // 简单的附件判断
                        try {
                            email.setHasAttachment(mimeMessage.getContent() instanceof MimeMultipart);
                        } catch (Exception e) {
                            email.setHasAttachment(false);
                        }

                        emailRepository.save(email);
                        log.info("Email saved for user: " + recipient);
                    });
                }
            } catch (Exception e) {
                log.error("Failed to parse email MIME", e);
            } finally {
                resetState();
            }
        }

        // 递归解析 MIME 正文 (支持纯文本和 HTML)
        private String getTextFromMimeMessage(jakarta.mail.Part part) throws Exception {
            if (part.isMimeType("text/plain")) {
                return (String) part.getContent();
            } else if (part.isMimeType("text/html")) {
                return (String) part.getContent(); // 优先返回 HTML
            } else if (part.isMimeType("multipart/*")) {
                MimeMultipart multipart = (MimeMultipart) part.getContent();
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < multipart.getCount(); i++) {
                    String partText = getTextFromMimeMessage(multipart.getBodyPart(i));
                    if (partText != null && !partText.isEmpty()) {
                        return partText; // 找到第一个文本部分即返回
                    }
                }
                return result.toString();
            }
            return null;
        }

        private void resetState() {
            sender = null;
            recipients.clear();
            dataBuilder.setLength(0);
            isDataMode = false;
        }

        private String extractEmail(String text) {
            int start = text.indexOf('<');
            int end = text.indexOf('>');
            if (start != -1 && end != -1) return text.substring(start + 1, end);
            String[] parts = text.split(":", 2);
            return parts.length > 1 ? parts[1].trim() : "";
        }
    }
}