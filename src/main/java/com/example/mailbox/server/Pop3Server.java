package com.example.mailbox.server;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Attachment;
import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.SystemLog;
import com.example.mailbox.repository.AttachmentRepository;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.example.mailbox.repository.SystemLogRepository;

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
    @Autowired private SystemLogRepository systemLogRepository;

    public void start() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(1100)) {
                log.info("POP3 Server started on port 1100");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(60000);
                    new Thread(new Pop3Handler(clientSocket)).start();
                }
            } catch (IOException e) { log.error("POP3 Server Error", e); }
        }).start();
    }


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

    class Pop3Handler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private Account currentUser;
        private List<Email> messageList = new ArrayList<>();
        private String pendingUsername;

        public Pop3Handler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                initStreams();
                writer.println("+OK POP3 server ready");

                String line;
                while ((line = reader.readLine()) != null) {
                    handleLine(line);
                }
            } catch (Exception e) {
                log.error("POP3 Fatal Error", e);
            } finally {
                try { socket.close(); } catch (IOException e) {}
            }
        }

        private void initStreams() throws IOException {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        }

        private void handleLine(String line) {
            try {
                String[] parts = line.trim().split("\\s+", 2);
                if (parts.length == 0) return;
                String command = parts[0].toUpperCase();
                String arg = parts.length > 1 ? parts[1] : "";

                log.debug("CMD: {}", command);
                dispatchCommand(command, arg);
                writer.flush();
            } catch (Exception e) {
                log.error("Error processing command: " + line, e);
                writer.println("-ERR Server error");
                writer.flush();
            }
        }

        private void dispatchCommand(String command, String arg) {
            switch (command) {
                case "USER" -> doUser(arg);
                case "PASS" -> doPass(arg);
                case "STAT" -> doStat();
                case "LIST" -> doList();
                case "RETR" -> doRetr(arg);
                case "UIDL" -> doUidl(arg);
                case "NOOP" -> writer.println("+OK");
                case "QUIT" -> { writer.println("+OK Bye"); try { socket.close(); } catch(IOException e){} }
                case "CAPA" -> doCapa();
                default -> writer.println("-ERR Unknown command");
            }
        }

        private void doUser(String arg) {
            pendingUsername = arg;
            writer.println("+OK User name accepted");
        }

        private void doPass(String password) {
            if (authenticate(pendingUsername, password)) {
                currentUser = userRepository.findByEmail(pendingUsername).orElse(null);
                if (currentUser != null) {
                    // 检查用户是否被封禁
                    if (!currentUser.isEnabled()) {
                        writer.println("-ERR Account disabled");
                        saveLog(SystemLog.LogType.POP3, pendingUsername, "LOGIN", "Account disabled", "FAILURE"); // 新增
                        return;
                    }
                    var page = emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(
                            pendingUsername, Email.FolderType.INBOX, org.springframework.data.domain.Pageable.unpaged());
                    messageList = page != null ? page.getContent() : new ArrayList<>();
                    writer.println("+OK Logged in");
                    saveLog(SystemLog.LogType.POP3, pendingUsername, "LOGIN", "Login successful", "SUCCESS"); // 新增
                } else {
                    writer.println("-ERR User not found");
                    saveLog(SystemLog.LogType.POP3, pendingUsername, "LOGIN", "User not found", "FAILURE"); // 新增
                }
            } else {
                writer.println("-ERR Auth failed");
            }
        }

        private void doStat() {
            if (!checkAuth()) return;
            long totalSize = messageList.stream().mapToLong(e -> getEmailSize(e)).sum();
            writer.println("+OK " + messageList.size() + " " + totalSize);
        }

        private void doList() {
            if (!checkAuth()) return;
            writer.println("+OK Listing follows");
            for (int i = 0; i < messageList.size(); i++) {
                writer.println((i + 1) + " " + getEmailSize(messageList.get(i)));
            }
            writer.println(".");
        }

        private void doRetr(String arg) {
            if (!checkAuth()) return;
            int index = parseIndex(arg);
            if (index >= 0 && index < messageList.size()) {
                writer.println("+OK message follows");
                saveLog(SystemLog.LogType.POP3, currentUser.getEmail(), "RETR", "Retrieved email ID: " + messageList.get(index).getId(), "SUCCESS"); // 新增
                try {
                    sendMimeMessage(messageList.get(index));
                } catch (Exception e) {
                    log.error("Send MIME error", e);
                }
                writer.println(".");
            } else {
                writer.println("-ERR Invalid message number");
            }
        }

        private void doUidl(String arg) {
            if (!checkAuth()) return;
            if (arg.isEmpty()) {
                writer.println("+OK Unique-ID listing follows");
                for (int i = 0; i < messageList.size(); i++) {
                    writer.println((i + 1) + " " + messageList.get(i).getId());
                }
                writer.println(".");
            } else {
                int index = parseIndex(arg);
                if (index >= 0 && index < messageList.size()) {
                    writer.println("+OK " + (index + 1) + " " + messageList.get(index).getId());
                } else {
                    writer.println("-ERR Invalid message number");
                }
            }
        }

        private void doCapa() {
            writer.println("+OK Capability list follows");
            writer.println("USER");
            writer.println("UIDL");
            writer.println(".");
        }

        private boolean checkAuth() {
            if (currentUser == null) {
                writer.println("-ERR Unauthorized");
                return false;
            }
            return true;
        }

        private boolean authenticate(String u, String p) {
            return u != null && userRepository.findByEmail(u)
                    .map(user -> passwordEncoder.matches(p, user.getPassword()))
                    .orElse(false);
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
            mimeMessage.writeTo(socket.getOutputStream());
            socket.getOutputStream().flush();
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
}