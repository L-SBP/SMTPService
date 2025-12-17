package com.example.mailbox.service;

import com.example.mailbox.entity.Attachment;
import com.example.mailbox.entity.Email;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

public interface AttachmentService {
    Attachment uploadAttachment(Long emailId, MultipartFile file);
    void uploadMultipleAttachments(Long emailId, MultipartFile[] files);

    // 新增：供 SMTP 服务器使用的接口
    void saveAttachmentFromStream(Email email, InputStream inputStream, String contentType, String fileName, long size);

    Attachment getAttachment(Long id);
}
