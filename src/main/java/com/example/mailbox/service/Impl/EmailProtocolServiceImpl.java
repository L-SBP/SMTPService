package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.Email.FolderType;
import com.example.mailbox.entity.Account;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.EmailProtocolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.search.FlagTerm;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import java.io.File;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;

/**
 * 邮件协议服务实现类
 * 使用POP3和SMTP协议与邮件服务器通信
 */
@Service
@Slf4j
public class EmailProtocolServiceImpl implements EmailProtocolService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public List<Email> receiveEmails(String email, String password, String host, int port, boolean ssl) {
        List<Email> emails = new ArrayList<>();
        Store store = null;
        Folder inbox = null;

        try {
            // 配置POP3属性
            Properties props = new Properties();
            props.put("mail.pop3.host", host);
            props.put("mail.pop3.port", String.valueOf(port));
            props.put("mail.pop3.auth", "true");
            // 同样禁用STARTTLS，避免本地服务器不支持导致的问题
            props.put("mail.pop3.starttls.enable", "false");

            if (ssl) {
                props.put("mail.pop3.ssl.enable", "true");
                props.put("mail.pop3.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            }

            // 获取Session
            Session session = Session.getInstance(props, null);
            session.setDebug(false);

            // 连接Store
            store = session.getStore(ssl ? "pop3s" : "pop3");
            store.connect(host, port, email, password);

            // 打开收件箱
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            // 获取邮件
            Message[] messages = inbox.getMessages();
            log.info("从POP3服务器接收到 {} 封邮件: {}", messages.length, email);

            // 倒序遍历（最新的在前），限制数量以提高性能
            // 实际应用中应该记录上次接收的UID，只接收新邮件
            // 这里简化处理，读取所有邮件
            for (int i = messages.length - 1; i >= 0; i--) {
                Message message = messages[i];
                Email emailEntity = parseMessageToEmail(message, email);
                if (emailEntity != null) {
                    emails.add(emailEntity);
                }
            }

        } catch (Exception e) {
            log.error("接收邮件失败: {}", e.getMessage(), e);
            // 这里不抛出异常，而是返回已获取的邮件（如果有）或空列表
            // 避免因连接问题导致整个页面崩溃
        } finally {
            try {
                if (inbox != null && inbox.isOpen()) {
                    inbox.close(false);
                }
                if (store != null && store.isConnected()) {
                    store.close();
                }
            } catch (Exception e) {
                log.error("关闭资源失败: {}", e.getMessage());
            }
        }

        return emails;
    }

    @Override
    public boolean sendEmail(String senderEmail, String password, List<String> recipients, String subject,
            String content, String host, int port, boolean ssl) {
        return sendEmail(senderEmail, password, recipients, subject, content, null, host, port, ssl);
    }

    @Override
    public boolean sendEmail(String senderEmail, String password, List<String> recipients, String subject,
            String content, List<File> attachments, String host, int port, boolean ssl) {
        // 兼容旧接口：默认禁用STARTTLS（适配本地自定义SMTP服务器）
        return sendEmailWithAuth(senderEmail, senderEmail, password, recipients, subject, content, attachments, host,
                port, ssl, false);
    }

    @Override
    public boolean sendEmailWithAuth(String fromEmail, String authUser, String authPassword, List<String> recipients,
            String subject, String content, List<File> attachments, String host, int port, boolean ssl,
            boolean starttls) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.ssl.trust", "*");

            // SSL(465) 与 STARTTLS(587) 二选一为主：按调用方参数配置
            props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
            if (starttls) {
                props.put("mail.smtp.starttls.required", "true");
            }

            if (ssl) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(authUser, authPassword);
                }
            });
            session.setDebug(false);

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));

            if (recipients != null && !recipients.isEmpty()) {
                Address[] addresses = new Address[recipients.size()];
                for (int i = 0; i < recipients.size(); i++) {
                    addresses[i] = new InternetAddress(recipients.get(i));
                }
                message.setRecipients(Message.RecipientType.TO, addresses);
            }

            message.setSubject(subject);

            Multipart multipart = new MimeMultipart();

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(content, "UTF-8");
            multipart.addBodyPart(textPart);

            if (attachments != null && !attachments.isEmpty()) {
                for (File file : attachments) {
                    if (file.exists()) {
                        MimeBodyPart attachmentPart = new MimeBodyPart();
                        DataSource source = new FileDataSource(file);
                        attachmentPart.setDataHandler(new DataHandler(source));
                        attachmentPart.setFileName(MimeUtility.encodeText(file.getName()));
                        multipart.addBodyPart(attachmentPart);
                    }
                }
            }

            message.setContent(multipart);
            Transport.send(message);
            log.info("邮件发送成功: {} (auth={}) -> {}", fromEmail, authUser, recipients);
            return true;

        } catch (Exception e) {
            log.error("发送邮件失败: {}", e.getMessage(), e);
            throw new RuntimeException("发送邮件失败: " + e.getMessage());
        }
    }

    @Override
    public Pop3ServerConfig getPop3Config(String email) {
        // 课程作业：统一使用自定义邮箱服务器
        // 所有@mb.com邮箱都使用同一个POP3服务器
        return new Pop3ServerConfig("localhost", 1100, false);
    }

    @Override
    public SmtpServerConfig getSmtpConfig(String email) {
        // 课程作业：统一使用自定义邮箱服务器
        // 所有@mb.com邮箱都使用同一个SMTP服务器
        return new SmtpServerConfig("localhost", 2525, false);
    }

    /**
     * 将JavaMail Message转换为Email实体
     */
    private Email parseMessageToEmail(Message message, String userEmail) throws MessagingException, IOException {
        try {
            Email emailEntity = new Email();

            // 发件人
            Address[] from = message.getFrom();
            if (from != null && from.length > 0) {
                emailEntity.setSender(((InternetAddress) from[0]).getAddress());
            }

            // 收件人
            Address[] to = message.getRecipients(Message.RecipientType.TO);
            if (to != null) {
                List<String> toList = new ArrayList<>();
                for (Address address : to) {
                    toList.add(((InternetAddress) address).getAddress());
                }
                emailEntity.setRecipients(toList);
            }

            // 抄送
            Address[] cc = message.getRecipients(Message.RecipientType.CC);
            if (cc != null) {
                List<String> ccList = new ArrayList<>();
                for (Address address : cc) {
                    ccList.add(((InternetAddress) address).getAddress());
                }
                emailEntity.setCc(ccList);
            }

            // 密送
            Address[] bcc = message.getRecipients(Message.RecipientType.BCC);
            if (bcc != null) {
                List<String> bccList = new ArrayList<>();
                for (Address address : bcc) {
                    bccList.add(((InternetAddress) address).getAddress());
                }
                emailEntity.setBcc(bccList);
            }

            // 主题
            emailEntity.setSubject(message.getSubject());

            // 内容
            Object content = message.getContent();
            if (content instanceof String) {
                emailEntity.setBody((String) content);
            } else if (content instanceof Multipart) {
                emailEntity.setBody(extractTextFromMultipart((Multipart) content));
            }

            // 附件标志
            emailEntity.setHasAttachment(message.getContentType().toLowerCase().contains("multipart"));

            // 已读标志
            emailEntity.setRead(message.isSet(Flags.Flag.SEEN));

            // 星标标志
            emailEntity.setStarred(message.isSet(Flags.Flag.FLAGGED));

            // 大小
            emailEntity.setSize(message.getSize());

            // 接收时间
            emailEntity.setReceivedTime(
                    message.getReceivedDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());

            // 文件夹类型
            emailEntity.setFolderType(FolderType.INBOX);

            // 关联用户
            Account user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("用户不存在: " + userEmail));
            emailEntity.setUser(user);

            return emailEntity;

        } catch (Exception e) {
            log.error("解析邮件内容失败: {}", e.getMessage());
            return null; // 跳过这封邮件
        }
    }

    /**
     * 从Multipart中提取文本内容
     */
    private String extractTextFromMultipart(Multipart multipart) throws MessagingException, IOException {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                content.append(bodyPart.getContent().toString());
            } else if (bodyPart.isMimeType("text/html")) {
                // 可以选择转换HTML为纯文本
                content.append(bodyPart.getContent().toString());
            } else if (bodyPart.getContent() instanceof Multipart) {
                content.append(extractTextFromMultipart((Multipart) bodyPart.getContent()));
            }
        }
        return content.toString();
    }

    /**
     * 创建邮件地址
     */
    private InternetAddress createAddress(String address) {
        try {
            return new InternetAddress(address);
        } catch (AddressException e) {
            log.error("无效的邮件地址: {}", address);
            throw new RuntimeException("无效的邮件地址: " + address);
        }
    }

    /**
     * 验证邮箱是否为mb.com域名
     */
    private boolean isValidMbEmail(String email) {
        return email != null && email.endsWith("@mb.com");
    }
}
