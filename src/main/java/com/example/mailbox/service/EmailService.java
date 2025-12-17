package com.example.mailbox.service;

import com.example.mailbox.entity.Email;
import com.example.mailbox.vo.Page;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.example.mailbox.dto.AttachmentDTO;
import java.util.List;

public interface EmailService {
    Page<Email> getInbox(String email, Pageable pageable);
    Page<Email> getSent(String email, Pageable pageable);
    Email getEmailById(Long id, String email);
    
    // 更新接口：支持附件
    void sendEmail(String senderEmail, List<String> to, String subject, String body, List<AttachmentDTO> attachments);
    // 保持旧接口兼容
    void sendEmail(String senderEmail, List<String> to, String subject, String body);

    void saveDraft(String senderEmail, List<String> to, String subject, String body);
    void markAsRead(Long id, String email, Boolean isRead);
    void markAsStarred(Long id, String email, Boolean isStarred);
    void deleteEmail(Long id, String email);
    Page<Email> searchEmails(String email, String query, int page, int size);
    
    Page<Email> getDrafts(String email, Pageable pageable);
    Page<Email> getTrash(String email, Pageable pageable);
    Page<Email> getStarred(String email, Pageable pageable);
}
