package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.Email.FolderType;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.EmailProtocolService;
import com.example.mailbox.service.EmailService;
import com.example.mailbox.service.OutboundEmailService;
import com.example.mailbox.service.TokenService;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.vo.Page;
import com.example.mailbox.config.OutboundSmtpProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.example.mailbox.dto.AttachmentDTO;
import com.example.mailbox.entity.Attachment;
import com.example.mailbox.repository.AttachmentRepository;
import java.io.File;
import java.util.ArrayList;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private EmailProtocolService emailProtocolService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private OutboundSmtpProperties outboundSmtpProperties;

    @Autowired
    private OutboundEmailService outboundEmailService;

    @Override
    public Page<Email> getInbox(String email, Pageable pageable) {
        return convertToPage(
                emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(email, FolderType.INBOX, pageable));
    }

    @Override
    public Page<Email> getSent(String email, Pageable pageable) {
        return convertToPage(
                emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(email, FolderType.SENT, pageable));
    }

    @Override
    public Page<Email> getDrafts(String email, Pageable pageable) {
        return convertToPage(
                emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(email, FolderType.DRAFT, pageable));
    }

    @Override
    public Page<Email> getTrash(String email, Pageable pageable) {
        return convertToPage(
                emailRepository.findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(email, FolderType.TRASH, pageable));
    }

    @Override
    public Page<Email> getStarred(String email, Pageable pageable) {
        return convertToPage(emailRepository.findByUserEmailAndIsStarredTrueOrderByReceivedTimeDesc(email, pageable));
    }

    @Override
    public Email getEmailById(Long id, String email) {
        Email emailEntity = emailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("邮件不存在"));

        if (!emailEntity.getUser().getEmail().equals(email)) {
            throw new RuntimeException("无权访问该邮件");
        }

        return emailEntity;
    }

    @Override
    @Transactional
    public void sendEmail(String senderEmail, List<String> to, String subject, String body) {
        sendEmail(senderEmail, to, subject, body, null);
    }

    @Override
    @Transactional
    public void sendEmail(String senderEmail, List<String> to, String subject, String body,
            List<AttachmentDTO> attachmentDTOs) {
        com.example.mailbox.entity.Account user = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        boolean hasExternal = hasExternalRecipient(to);

        // 准备附件文件
        List<File> attachmentFiles = new ArrayList<>();
        List<Attachment> attachments = new ArrayList<>();

        if (attachmentDTOs != null && !attachmentDTOs.isEmpty()) {
            for (AttachmentDTO dto : attachmentDTOs) {
                File file = null;
                if (dto.getId() != null) {
                    Attachment attachment = attachmentRepository.findById(dto.getId()).orElse(null);
                    if (attachment != null) {
                        attachments.add(attachment);
                        if (attachment.getFilePath() != null) {
                            file = new File(attachment.getFilePath());
                        }
                    }
                } else if (dto.getFilePath() != null) {
                    file = new File(dto.getFilePath());
                }

                if (file != null) {
                    if (file.exists()) {
                        attachmentFiles.add(file);
                        System.out.println("添加附件: " + file.getAbsolutePath());
                    } else {
                        System.err.println("附件文件不存在: " + file.getAbsolutePath());
                    }
                }
            }
        }

        boolean success = false;
        try {
            if (hasExternal) {
                // 使用 OutboundEmailService 发送外部邮件
                OutboundEmailService.SendResult result = outboundEmailService.sendToExternal(
                        senderEmail,
                        to,
                        null, // cc
                        null, // bcc
                        subject,
                        body,
                        attachmentDTOs,
                        false // 纯文本
                );

                if (!result.isSuccess()) {
                    throw new RuntimeException(result.getMessage());
                }
                // 外发邮件的保存由 OutboundEmailService 内部处理
                return;
            } else {
                // 内部收件人：走本地自定义SMTP服务器（token认证）
                var config = emailProtocolService.getSmtpConfig(senderEmail);

                String token = jwtUtil.generateToken(senderEmail);
                tokenService.storeToken(token, senderEmail, jwtUtil.getExpiration());

                System.out.println(
                        "开始调用本地SMTP服务发送邮件: sender=" + senderEmail + ", token=" + token.substring(0, 10) + "...");
                success = emailProtocolService.sendEmail(
                        senderEmail,
                        token,
                        to,
                        subject,
                        body,
                        attachmentFiles,
                        config.getHost(),
                        config.getPort(),
                        config.isSsl());
            }
        } catch (Exception e) {
            System.err.println("SMTP发送异常: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("SMTP发送失败: " + e.getMessage());
        }

        if (!success) {
            throw new RuntimeException("邮件发送失败");
        }

        // 保存到已发送（仅内部邮件，外部邮件由 OutboundEmailService 处理）
        Email email = new Email();
        email.setSender(senderEmail);
        email.setRecipients(to);
        email.setSubject(subject);
        email.setBody(body);
        email.setFolderType(FolderType.SENT);
        email.setUser(user);
        email.setReceivedTime(java.time.LocalDateTime.now());
        email.setRead(true);
        email.setHasAttachment(!attachments.isEmpty());

        Email savedEmail = emailRepository.save(email);

        // 关联附件到邮件
        for (Attachment attachment : attachments) {
            attachment.setEmail(savedEmail);
            attachmentRepository.save(attachment);
        }

        // 处理新上传但未持久化的附件
        if (attachmentDTOs != null) {
            for (AttachmentDTO dto : attachmentDTOs) {
                if (dto.getId() == null && dto.getFilePath() != null) {
                    Attachment newAttachment = new Attachment();
                    newAttachment.setEmail(savedEmail);
                    newAttachment.setFileName(dto.getFileName());
                    newAttachment.setFileSize(dto.getFileSize());
                    newAttachment.setContentType(dto.getContentType());
                    newAttachment.setFilePath(dto.getFilePath());
                    attachmentRepository.save(newAttachment);
                }
            }
        }
    }

    private boolean hasExternalRecipient(List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return false;
        }
        for (String recipient : recipients) {
            if (recipient == null) {
                continue;
            }
            String normalized = recipient.trim().toLowerCase();
            if (!normalized.endsWith("@mb.com")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void saveDraft(String senderEmail, List<String> to, String subject, String body) {
        com.example.mailbox.entity.Account user = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Email email = new Email();
        email.setSender(senderEmail);
        email.setRecipients(to);
        email.setSubject(subject);
        email.setBody(body);
        email.setFolderType(FolderType.DRAFT);
        email.setUser(user);
        email.setReceivedTime(java.time.LocalDateTime.now());
        email.setRead(true);
        emailRepository.save(email);
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
        if (emailEntity.getFolderType() == FolderType.TRASH) {
            emailRepository.delete(emailEntity);
        } else {
            emailEntity.setFolderType(FolderType.TRASH);
            emailRepository.save(emailEntity);
        }
    }

    @Override
    public Page<Email> searchEmails(String email, String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return convertToPage(emailRepository.searchByEmailAndContent(email, query, pageable));
    }

    private Page<Email> convertToPage(org.springframework.data.domain.Page<Email> springPage) {
        return new Page<>(
                springPage.getContent(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.getSize(),
                springPage.getNumber(),
                springPage.isFirst(),
                springPage.isLast());
    }
}
