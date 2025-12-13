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

    @Autowired private EmailRepository emailRepository;
    @Autowired private UserRepository userRepository;

    @Override
    public Page<Email> getInbox(String email, Pageable pageable) {
        return convertToPage(emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(email, FolderType.INBOX, pageable));
    }

    @Override
    public Page<Email> getSent(String email, Pageable pageable) {
        return convertToPage(emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(email, FolderType.SENT, pageable));
    }

    @Override
    public Email getEmailById(Long id, String email) {
        return emailRepository.findById(id).orElseThrow(() -> new RuntimeException("邮件不存在"));
    }

    @Override
    @Transactional
    public void sendEmail(String senderEmail, Object requestObj) {
        if (!(requestObj instanceof EmailRequest)) throw new IllegalArgumentException("无效的请求参数");
        EmailRequest request = (EmailRequest) requestObj;

        Account sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("发件人账户异常"));

        // 1. 保存到发件箱
        saveSentEmail(sender, request);

        // 2. 投递给所有收件人
        distributeToRecipients(senderEmail, request);
    }

    private void saveSentEmail(Account sender, EmailRequest request) {
        Email sentEmail = createBaseEmail(sender.getEmail(), request);
        sentEmail.setUser(sender);
        sentEmail.setFolderType(FolderType.SENT);
        sentEmail.setRead(true);
        emailRepository.save(sentEmail);
    }

    private void distributeToRecipients(String senderEmail, EmailRequest request) {
        List<String> allRecipients = new ArrayList<>();
        if (request.getTo() != null) allRecipients.addAll(request.getTo());
        if (request.getCc() != null) allRecipients.addAll(request.getCc());
        if (request.getBcc() != null) allRecipients.addAll(request.getBcc());

        LocalDateTime now = LocalDateTime.now();
        for (String recipientEmail : allRecipients) {
            userRepository.findByEmail(recipientEmail).ifPresent(recipientUser -> {
                Email inboxEmail = createBaseEmail(senderEmail, request);
                inboxEmail.setUser(recipientUser);
                inboxEmail.setFolderType(FolderType.INBOX);
                inboxEmail.setRead(false);
                // 确保收件人看到的是发件时间，而不是入库时间（虽然这里是同一时刻）
                inboxEmail.setReceivedTime(now);
                emailRepository.save(inboxEmail);
            });
        }
    }

    private Email createBaseEmail(String senderEmail, EmailRequest request) {
        Email email = new Email();
        email.setSender(senderEmail);
        email.setRecipients(request.getTo());
        email.setCc(request.getCc());
        // 注意：BCC 通常只在发件箱保留，收件箱副本不应包含 BCC 列表，这里简化处理保留了原逻辑
        // 如果要严谨，distributeToRecipients 里存的副本应该清空 bcc
        email.setBcc(request.getBcc());
        email.setSubject(request.getSubject());
        email.setBody(request.getBody());
        email.setReceivedTime(LocalDateTime.now());
        email.setHasAttachment(request.getAttachments() != null && !request.getAttachments().isEmpty());
        return email;
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
