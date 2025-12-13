package com.example.mailbox.service;

import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {
    void uploadAttachment(Long emailId, MultipartFile file);
    void uploadMultipleAttachments(Long emailId, MultipartFile[] files);
}
