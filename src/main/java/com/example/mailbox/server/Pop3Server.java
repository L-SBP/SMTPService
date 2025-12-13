package com.example.mailbox.server;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Attachment;
import com.example.mailbox.entity.Email;
import com.example.mailbox.repository.AttachmentRepository;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
@Component
public class Pop3Server {

    @Autowired private UserRepository userRepository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    public void start() {
        new Thread(() -> {
            int port = 1100;
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log.info("POP3 Server started on port " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    // 设置超时防止僵尸连接
                    clientSocket.setSoTimeout(60000);
                    new Thread(new Pop3Handler(clientSocket)).start();
                }
            } catch (IOException e) { log.error("POP3 Server Error", e); }
        }).start();
    }

    class Pop3Handler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private Account currentUser;
        private List<Email> messageList = new ArrayList<>(); // 初始化防止空指针

        public Pop3Handler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                writer.println("+OK POP3 server ready");

                String line;
                String pendingUsername = null;

                while ((line = reader.readLine()) != null) {
                    try {
                        String[] parts = line.trim().split("\\s+", 2);
                        if (parts.length == 0) continue;

                        String command = parts[0].toUpperCase();
                        String arg = parts.length > 1 ? parts[1] : "";

                        log.debug("Received POP3 command: {}", command);

                        if ("USER".equals(command)) {
                            pendingUsername = arg;
                            writer.println("+OK User name accepted");
                        } else if ("PASS".equals(command)) {
                            if (authenticate(pendingUsername, arg)) {
                                currentUser = userRepository.findByEmail(pendingUsername).orElse(null);
                                if (currentUser != null) {
                                    // 确保获取非空列表
                                    var page = emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(
                                            pendingUsername, Email.FolderType.INBOX, org.springframework.data.domain.Pageable.unpaged()
                                    );
                                    messageList = page != null ? page.getContent() : new ArrayList<>();
                                    writer.println("+OK Logged in");
                                } else {
                                    writer.println("-ERR User not found");
                                }
                            } else {
                                writer.println("-ERR Auth failed");
                            }
                        } else if ("STAT".equals(command)) {
                            if (!checkAuth()) continue;
                            long totalSize = 0;
                            for (Email e : messageList) {
                                totalSize += (e.getBody() == null ? 0 : e.getBody().length());
                            }
                            writer.println("+OK " + messageList.size() + " " + totalSize);
                        } else if ("LIST".equals(command)) {
                            if (!checkAuth()) continue;
                            writer.println("+OK Listing follows");
                            for (int i = 0; i < messageList.size(); i++) {
                                int size = (messageList.get(i).getBody() == null ? 0 : messageList.get(i).getBody().length());
                                writer.println((i + 1) + " " + size);
                            }
                            writer.println(".");
                        } else if ("RETR".equals(command)) {
                            if (!checkAuth()) continue;
                            int index = -1;
                            try { index = Integer.parseInt(arg) - 1; } catch (NumberFormatException e) {
                                writer.println("-ERR Invalid message number format");
                                continue;
                            }

                            if (index >= 0 && index < messageList.size()) {
                                Email email = messageList.get(index);
                                writer.println("+OK message follows");
                                try {
                                    // 关键：确保这里不会抛出导致连接断开的异常
                                    sendMimeMessage(email);
                                } catch (Exception e) {
                                    log.error("Failed to construct MIME message for email ID: " + email.getId(), e);
                                    // 如果已经在传输过程中出错，客户端可能已经收到部分数据，这里再发错误可能没用，但记录日志很关键
                                }
                                writer.println(".");
                            } else {
                                writer.println("-ERR Invalid message number: " + (index + 1));
                            }
                        } else if ("QUIT".equals(command)) {
                            writer.println("+OK Bye"); break;
                        } else if ("CAPA".equals(command)) {
                            writer.println("+OK Capability list follows");
                            writer.println("USER");
                            writer.println("UIDL");
                            writer.println(".");
                        } else if ("UIDL".equals(command)) {
                            if (!checkAuth()) continue;
                            if (arg.isEmpty()) {
                                writer.println("+OK Unique-ID listing follows");
                                for (int i = 0; i < messageList.size(); i++) {
                                    writer.println((i + 1) + " " + messageList.get(i).getId());
                                }
                                writer.println(".");
                            } else {
                                int index = -1;
                                try { index = Integer.parseInt(arg) - 1; } catch (NumberFormatException e) {}
                                if (index >= 0 && index < messageList.size()) {
                                    writer.println("+OK " + (index + 1) + " " + messageList.get(index).getId());
                                } else {
                                    writer.println("-ERR Invalid message number");
                                }
                            }
                        } else if ("NOOP".equals(command)) {
                            writer.println("+OK");
                        } else {
                            writer.println("-ERR Unknown command");
                        }

                        writer.flush(); // 确保数据立即发送

                    } catch (Exception cmdEx) {
                        log.error("Error processing POP3 command: " + line, cmdEx);
                        writer.println("-ERR Server error processing command");
                        writer.flush();
                    }
                }
            } catch (Exception e) {
                log.error("POP3 Fatal Error", e);
            } finally {
                try { socket.close(); } catch (IOException e) {}
            }
        }

        private boolean checkAuth() {
            if (currentUser == null) {
                writer.println("-ERR Unauthorized");
                writer.flush();
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

        private void sendMimeMessage(Email email) throws Exception {
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);

            mimeMessage.setFrom(new InternetAddress(email.getSender() != null ? email.getSender() : "unknown@server"));

            if (email.getRecipients() != null && !email.getRecipients().isEmpty()) {
                try {
                    mimeMessage.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(String.join(",", email.getRecipients())));
                } catch (Exception e) {
                    mimeMessage.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse("undisclosed-recipients@server"));
                }
            }

            mimeMessage.setSubject(email.getSubject() != null ? email.getSubject() : "", "UTF-8");

            if (email.getReceivedTime() != null) {
                mimeMessage.setSentDate(java.sql.Timestamp.valueOf(email.getReceivedTime()));
            } else {
                mimeMessage.setSentDate(new java.util.Date());
            }

            Multipart multipart = new MimeMultipart();

            // 正文
            BodyPart textPart = new MimeBodyPart();
            textPart.setContent(email.getBody() != null ? email.getBody() : "", "text/html; charset=UTF-8");
            multipart.addBodyPart(textPart);

            // 附件
            if (Boolean.TRUE.equals(email.getHasAttachment())) {
                List<Attachment> attachments = attachmentRepository.findByEmailId(email.getId());
                if (attachments != null) { // 判空
                    for (Attachment att : attachments) {
                        try {
                            MimeBodyPart attachmentPart = new MimeBodyPart();
                            FileDataSource source = new FileDataSource(att.getFilePath());
                            attachmentPart.setDataHandler(new DataHandler(source));
                            attachmentPart.setFileName(MimeUtility.encodeText(att.getFileName() != null ? att.getFileName() : "unknown"));
                            multipart.addBodyPart(attachmentPart);
                        } catch (Exception e) {
                            log.error("Failed to attach file: " + att.getFileName(), e);
                        }
                    }
                }
            }

            mimeMessage.setContent(multipart);
            mimeMessage.saveChanges(); // 确保 header 更新
            mimeMessage.writeTo(socket.getOutputStream());
            socket.getOutputStream().flush(); // 强制刷新流
        }
    }
}