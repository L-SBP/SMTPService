package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.Email.FolderType;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.EmailProtocolService;
import com.example.mailbox.service.EmailService;
import com.example.mailbox.vo.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailProtocolService emailProtocolService;

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

    @Override
    public Page<Email> searchEmails(String email, String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        var emails = emailRepository.searchByEmailAndContent(email, query, pageable);
        return convertToPage(emails);
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
