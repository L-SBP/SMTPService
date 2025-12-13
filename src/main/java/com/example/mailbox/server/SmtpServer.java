package com.example.mailbox.server;

import com.example.mailbox.entity.Email;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.AttachmentService;
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

/**
 * SMTP 服务器实现
 * 负责监听 25/2500 端口，接收邮件，解析 MIME 格式并存入数据库
 */
@Slf4j
@Component
public class SmtpServer {

    @Autowired private EmailRepository emailRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AttachmentService attachmentService;

    // 静态黑名单配置 (实际生产中建议改为数据库查询)
    private static final List<String> BLACKLIST_IPS = Arrays.asList("192.168.1.100");
    private static final List<String> BLACKLIST_EMAILS = Arrays.asList("spammer@bad.com");

    /**
     * 启动 SMTP 服务监听
     */
    public void start() {
        new Thread(() -> {
            int port = 2500; // 开发环境使用 2500，生产环境改为 25
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log.info("SMTP Server started on port " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    if (isBlocked(clientSocket)) continue; // 黑名单拦截
                    new Thread(new SmtpHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                log.error("SMTP Server Error", e);
            }
        }).start();
    }

    /**
     * 检查客户端 IP 是否在黑名单中
     */
    private boolean isBlocked(Socket socket) throws IOException {
        String clientIp = socket.getInetAddress().getHostAddress();
        if (BLACKLIST_IPS.contains(clientIp)) {
            log.warn("Blocked connection from blacklisted IP: " + clientIp);
            socket.close();
            return true;
        }
        return false;
    }

    /**
     * 单个 SMTP 连接处理器
     */
    class SmtpHandler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;

        // 会话状态变量
        private String sender;
        private List<String> recipients = new ArrayList<>();
        private StringBuilder dataBuilder = new StringBuilder();
        private boolean isDataMode = false;

        public SmtpHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                initStreams();
                writer.println("220 Welcome to Mailbox SMTP Server");

                String line;
                while ((line = reader.readLine()) != null) {
                    if (isDataMode) {
                        handleDataLine(line);
                    } else {
                        handleCommand(line);
                    }
                }
            } catch (Exception e) {
                log.error("SMTP Handler Error", e);
            } finally {
                closeSocket();
            }
        }

        private void initStreams() throws IOException {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(socket.getOutputStream(), true);
        }

        /**
         * 处理 DATA 模式下的邮件正文输入
         */
        private void handleDataLine(String line) {
            if (".".equals(line)) {
                isDataMode = false;
                processAndSaveEmail(); // 核心：解析并保存邮件
                writer.println("250 OK Message accepted");
            } else {
                dataBuilder.append(line).append("\r\n"); // SMTP 规范要求 CRLF
            }
        }

        /**
         * 处理 SMTP 指令
         */
        private void handleCommand(String line) {
            String cmd = line.trim().toUpperCase();
            if (cmd.startsWith("HELO") || cmd.startsWith("EHLO")) {
                writer.println("250 Hello " + socket.getInetAddress().getHostAddress());
            } else if (cmd.startsWith("MAIL FROM:")) {
                handleMailFrom(line);
            } else if (cmd.startsWith("RCPT TO:")) {
                handleRcptTo(line);
            } else if (cmd.startsWith("DATA")) {
                writer.println("354 End data with <CR><LF>.<CR><LF>");
                isDataMode = true;
            } else if (cmd.startsWith("QUIT")) {
                writer.println("221 Bye");
                closeSocket();
            } else if (cmd.equals("RSET")) {
                resetState();
                writer.println("250 OK");
            } else {
                writer.println("500 Unknown command");
            }
        }

        private void handleMailFrom(String line) {
            sender = extractEmail(line);
            if (BLACKLIST_EMAILS.contains(sender)) {
                writer.println("550 Sender blocked");
            } else {
                writer.println("250 OK");
            }
        }

        private void handleRcptTo(String line) {
            recipients.add(extractEmail(line));
            writer.println("250 OK");
        }

        /**
         * 解析并保存邮件的主流程
         */
        private void processAndSaveEmail() {
            if (recipients.isEmpty()) return;
            try {
                // 1. 使用 JavaMail 解析原始数据
                MimeMessage mimeMessage = parseMimeMessage();

                // 2. 提取正文和附件
                ParsedEmailData emailData = extractEmailData(mimeMessage);

                // 3. 分发给所有收件人并保存到数据库
                saveEmailToRecipients(emailData);
            } catch (Exception e) {
                log.error("Failed to parse/save email", e);
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

        private void closeSocket() {
            try { socket.close(); } catch (IOException e) {}
        }

        private String extractEmail(String text) {
            int start = text.indexOf('<'); int end = text.indexOf('>');
            if (start != -1 && end != -1) return text.substring(start + 1, end);
            String[] parts = text.split(":", 2);
            return parts.length > 1 ? parts[1].trim() : "";
        }
    }
}