package com.example.mailbox.service;

import com.example.mailbox.entity.Email;
import com.example.mailbox.vo.Page;

import org.springframework.data.domain.Pageable;

public interface EmailService {
    Page<Email> getInbox(String email, Pageable pageable);
    Page<Email> getSent(String email, Pageable pageable);
    Email getEmailById(Long id, String email);
    void sendEmail(String senderEmail, Object request);
    void markAsRead(Long id, String email, Boolean isRead);
    void markAsStarred(Long id, String email, Boolean isStarred);
    void deleteEmail(Long id, String email);
}
