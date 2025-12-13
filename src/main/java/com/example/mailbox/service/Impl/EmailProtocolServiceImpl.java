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
        
        try {
            // 配置POP3属性
            Properties props = new Properties();
            props.put("mail.store.protocol", "pop3");
            if (ssl) {
                props.put("mail.pop3.ssl.enable", "true");
                props.put("mail.pop3.ssl.trust", "*");
            } else {
                props.put("mail.pop3.starttls.enable", "true");
            }
            props.put("mail.pop3.host", host);
            props.put("mail.pop3.port", String.valueOf(port));

            // 创建会话
            Session session = Session.getInstance(props);
            session.setDebug(false); // 生产环境关闭调试

            // 连接POP3服务器
            Store store = session.getStore("pop3");
            store.connect(host, port, email, password);

            // 打开收件箱文件夹
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            // 获取邮件
            Message[] messages = inbox.getMessages();
            log.info("从POP3服务器获取到 {} 封邮件", messages.length);

            // 处理邮件
            for (Message message : messages) {
                try {
                    Email emailEntity = parseMessageToEmail(message, email);
                    if (emailEntity != null) {
                        emails.add(emailEntity);
                    }
                } catch (Exception e) {
                    log.error("解析邮件失败: {}", e.getMessage());
                    continue;
                }
            }

            // 关闭连接
            inbox.close(false);
            store.close();

        } catch (Exception e) {
            log.error("接收邮件失败: {}", e.getMessage(), e);
            throw new RuntimeException("接收邮件失败: " + e.getMessage());
        }

        return emails;
    }


    @Override
    public boolean sendEmail(String senderEmail, String password, String host, int port, boolean ssl) {
        try {
            // 配置SMTP属性
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.ssl.trust", "*");

            if (ssl) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            }

            // 创建会话
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(senderEmail, password);
                }
            });

            session.setDebug(false); // 生产环境关闭调试

            // 创建邮件
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("target@mb.com"));
            message.setSubject("Test Email");
            message.setText("This is a test email.");

            // SMTP协议交互流程：
            // 1. 客户端连接到服务器的25端口
            // 2. 服务器返回状态码220，表示服务已就绪
            // 3. 客户端发送HELO或EHLO命令，告知服务器自己的域名
            // 4. 服务器返回状态码250，表示命令成功
            // 5. 客户端发送MAIL FROM命令，指定发件人邮箱
            // 6. 客户端发送RCPT TO命令，指定收件人邮箱
            // 7. 客户端发送DATA命令，开始输入邮件内容
            // 8. 客户端发送邮件正文
            // 9. 客户端用单独一行的句点表示邮件输入结束
            // 10. 服务器返回状态码250，表示邮件接收成功
            // 11. 客户端发送QUIT命令，请求关闭连接
            // 12. 服务器返回状态码221，表示连接已关闭

            // 发送邮件
            Transport.send(message);
            log.info("邮件发送成功: {} -> target@mb.com", senderEmail);

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
        return new Pop3ServerConfig("pop.mb.com", 110, false);
    }

    @Override
    public SmtpServerConfig getSmtpConfig(String email) {
        // 课程作业：统一使用自定义邮箱服务器
        // 所有@mb.com邮箱都使用同一个SMTP服务器
        return new SmtpServerConfig("smtp.mb.com", 25, false);
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
            emailEntity.setReceivedTime(message.getReceivedDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            
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
