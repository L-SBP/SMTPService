package com.example.mailbox.server;

import com.example.mailbox.entity.Email;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.AttachmentService;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Slf4j
@Component
public class SmtpServer {

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttachmentService attachmentService;

    // 简单的黑名单列表（模拟 PDF 中的地址过滤）
    private static final List<String> BLACKLIST_IPS = Arrays.asList("192.168.1.100");
    private static final List<String> BLACKLIST_EMAILS = Arrays.asList("spammer@bad.com");

    public void start() {
        new Thread(() -> {
            int port = 2500;
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log.info("SMTP Server started on port " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    // 1. IP 过滤
                    String clientIp = clientSocket.getInetAddress().getHostAddress();
                    if (BLACKLIST_IPS.contains(clientIp)) {
                        log.warn("Blocked connection from blacklisted IP: " + clientIp);
                        clientSocket.close();
                        continue;
                    }
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

        public SmtpHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new PrintWriter(socket.getOutputStream(), true);

                writer.println("220 Welcome to Mailbox SMTP Server");

                String line;
                while ((line = reader.readLine()) != null) {
                    if (isDataMode) {
                        if (".".equals(line)) {
                            isDataMode = false;
                            processAndSaveEmail();
                            writer.println("250 OK Message accepted");
                        } else {
                            dataBuilder.append(line).append("\r\n");
                        }
                        continue;
                    }

                    String cmd = line.trim().toUpperCase();
                    if (cmd.startsWith("HELO") || cmd.startsWith("EHLO")) {
                        writer.println("250 Hello " + socket.getInetAddress().getHostAddress());
                    } else if (cmd.startsWith("MAIL FROM:")) {
                        sender = extractEmail(line);
                        // 2. 邮件地址过滤
                        if (BLACKLIST_EMAILS.contains(sender)) {
                            writer.println("550 Sender blocked");
                            return;
                        }
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
                Session session = Session.getDefaultInstance(new Properties());
                MimeMessage mimeMessage = new MimeMessage(session,
                        new ByteArrayInputStream(dataBuilder.toString().getBytes(StandardCharsets.UTF_8)));

                String subject = mimeMessage.getSubject();

                // 递归解析内容
                StringBuilder textBody = new StringBuilder();
                List<SavedAttachment> attachments = new ArrayList<>();
                parseMimeContent(mimeMessage, textBody, attachments);

                for (String recipient : recipients) {
                    userRepository.findByEmail(recipient).ifPresent(user -> {
                        Email email = new Email();
                        email.setSender(sender != null ? sender : "unknown");
                        email.setRecipients(new ArrayList<>(recipients));
                        email.setSubject(subject != null ? subject : "(No Subject)");
                        email.setBody(textBody.toString()); // 只存文本/HTML正文
                        email.setUser(user);
                        email.setFolderType(Email.FolderType.INBOX);
                        email.setReceivedTime(LocalDateTime.now());
                        email.setHasAttachment(!attachments.isEmpty());

                        // 先保存邮件以获取 ID
                        Email savedEmail = emailRepository.save(email);

                        // 保存附件关联
                        for (SavedAttachment att : attachments) {
                            attachmentService.saveAttachmentFromStream(
                                    savedEmail,
                                    new ByteArrayInputStream(att.data), // 需要重新创建流
                                    att.contentType,
                                    att.fileName,
                                    att.data.length
                            );
                        }
                        log.info("Email saved for user: " + recipient + ", attachments: " + attachments.size());
                    });
                }
            } catch (Exception e) {
                log.error("Failed to parse email MIME", e);
            } finally {
                resetState();
            }
        }

        // 临时内部类用于暂存附件数据
        class SavedAttachment {
            byte[] data;
            String fileName;
            String contentType;
            SavedAttachment(byte[] data, String fileName, String contentType) {
                this.data = data; this.fileName = fileName; this.contentType = contentType;
            }
        }

        private void parseMimeContent(Part part, StringBuilder textBody, List<SavedAttachment> attachments) throws Exception {
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) ||
                    (part.getFileName() != null && !part.getFileName().isEmpty())) {
                // 这是一个附件
                InputStream is = part.getInputStream();
                byte[] data = is.readAllBytes(); // 简单起见读入内存，大文件建议优化
                attachments.add(new SavedAttachment(data, part.getFileName(), part.getContentType()));
            } else if (part.isMimeType("text/*")) {
                // 这是一个文本正文
                textBody.append((String) part.getContent()).append("\n");
            } else if (part.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) part.getContent();
                for (int i = 0; i < multipart.getCount(); i++) {
                    parseMimeContent(multipart.getBodyPart(i), textBody, attachments);
                }
            }
        }

        private void resetState() { sender = null; recipients.clear(); dataBuilder.setLength(0); isDataMode = false; }
        private String extractEmail(String text) {
            int start = text.indexOf('<'); int end = text.indexOf('>');
            if (start != -1 && end != -1) return text.substring(start + 1, end);
            String[] parts = text.split(":", 2); return parts.length > 1 ? parts[1].trim() : "";
        }
    }
}