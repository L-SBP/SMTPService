package com.example.mailbox.service.Impl;

import com.example.mailbox.controller.EmailController.EmailRequest;
import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.Email.FolderType;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.EmailService;
import com.example.mailbox.vo.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Page<Email> getInbox(String email, Pageable pageable) {
        var emails = emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(
            email, FolderType.INBOX, pageable
        );

        return convertToPage(emails);
    }

    @Override
    public Page<Email> getSent(String email, Pageable pageable) {
        var emails = emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(
            email, FolderType.SENT, pageable
        );

        return convertToPage(emails);
    }

    @Override
    public Email getEmailById(Long id, String email) {
        return emailRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("邮件不存在"));
    }

    /**
     * 实现 Web 端发送邮件逻辑
     */
    @Override
    @Transactional
    public void sendEmail(String senderEmail, Object requestObj) {
        if (!(requestObj instanceof EmailRequest)) {
            throw new IllegalArgumentException("无效的请求参数");
        }
        EmailRequest request = (EmailRequest) requestObj;

        // 1. 获取发件人用户
        Account sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("发件人账户异常"));

        LocalDateTime now = LocalDateTime.now();

        // 2. 保存到发件人的“已发送”箱 (SENT)
        Email sentEmail = new Email();
        sentEmail.setSender(senderEmail);
        sentEmail.setRecipients(request.getTo());
        sentEmail.setCc(request.getCc());
        sentEmail.setBcc(request.getBcc());
        sentEmail.setSubject(request.getSubject());
        sentEmail.setBody(request.getBody());
        sentEmail.setUser(sender); // 归属于发件人
        sentEmail.setFolderType(FolderType.SENT);
        sentEmail.setReceivedTime(now);
        sentEmail.setRead(true); // 自己发的当然已读
        // 注意：Web端发送的附件处理比较复杂，这里暂存为无附件，后续需通过upload接口关联
        sentEmail.setHasAttachment(request.getAttachments() != null && !request.getAttachments().isEmpty());
        emailRepository.save(sentEmail);

        // 3. 投递给所有收件人 (INBOX)
        List<String> allRecipients = new ArrayList<>();
        if (request.getTo() != null) allRecipients.addAll(request.getTo());
        if (request.getCc() != null) allRecipients.addAll(request.getCc());
        if (request.getBcc() != null) allRecipients.addAll(request.getBcc());

        for (String recipientEmail : allRecipients) {
            // 查找收件人是否存在（只投递给本站存在的用户）
            userRepository.findByEmail(recipientEmail).ifPresent(recipientUser -> {
                Email inboxEmail = new Email();
                inboxEmail.setSender(senderEmail);
                inboxEmail.setRecipients(request.getTo()); // 收件人看到原本的收件列表
                inboxEmail.setCc(request.getCc());
                inboxEmail.setSubject(request.getSubject());
                inboxEmail.setBody(request.getBody());
                inboxEmail.setUser(recipientUser); // 归属于收件人
                inboxEmail.setFolderType(FolderType.INBOX);
                inboxEmail.setReceivedTime(now);
                inboxEmail.setRead(false);
                inboxEmail.setHasAttachment(request.getAttachments() != null && !request.getAttachments().isEmpty());
                emailRepository.save(inboxEmail);
            });
        }
    }

    @Override
    public void markAsRead(Long id, String email, Boolean isRead) {
        Email emailEntity = getEmailById(id, email);
        emailEntity.setRead(isRead);
        emailRepository.save(emailEntity);
    }

    @Override
    public void markAsStarred(Long id, String email, Boolean isStarred) {
        Email emailEntity = getEmailById(id, email);
        emailEntity.setStarred(isStarred);
        emailRepository.save(emailEntity);
    }

    @Override
    public void deleteEmail(Long id, String email) {
        Email emailEntity = getEmailById(id, email);
        emailEntity.setFolderType(FolderType.TRASH);
        emailRepository.save(emailEntity);
    }

    private Page<Email> convertToPage(org.springframework.data.domain.Page<Email> springPage) {
        return new Page<>(
            springPage.getContent(),
            springPage.getTotalElements(),
            springPage.getTotalPages(),
            springPage.getSize(),
            springPage.getNumber(),
            springPage.isFirst(),
            springPage.isLast()
        );
    }
}
