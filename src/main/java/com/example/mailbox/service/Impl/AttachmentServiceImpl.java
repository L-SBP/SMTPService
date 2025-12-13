package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Attachment;
import com.example.mailbox.entity.Email;
import com.example.mailbox.repository.AttachmentRepository;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.service.AttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class AttachmentServiceImpl implements AttachmentService {

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    private final Path uploadPath = Paths.get("uploads/attachments");

    @Override
    public void uploadAttachment(Long emailId, MultipartFile file) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new RuntimeException("邮件不存在"));
        try {
            saveFile(email, file.getInputStream(), file.getContentType(), file.getOriginalFilename(), file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("附件上传失败", e);
        }
    }

    @Override
    public void uploadMultipleAttachments(Long emailId, MultipartFile[] files) {
        for (MultipartFile file : files) {
            uploadAttachment(emailId, file);
        }
    }

    @Override
    public void saveAttachmentFromStream(Email email, InputStream inputStream, String contentType, String fileName, long size) {
        saveFile(email, inputStream, contentType, fileName, size);
    }

    // 通用保存逻辑
    private void saveFile(Email email, InputStream inputStream, String contentType, String fileName, long size) {
        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 生成安全的文件名
            String savedFileName = UUID.randomUUID() + "_" + (fileName != null ? fileName : "unknown");
            Path filePath = uploadPath.resolve(savedFileName);

            // 保存文件
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

            // 存入数据库
            Attachment attachment = new Attachment();
            attachment.setEmail(email);
            attachment.setFileName(fileName);
            attachment.setFileSize(size);
            attachment.setContentType(contentType);
            attachment.setFilePath(filePath.toString());

            attachmentRepository.save(attachment);
        } catch (IOException e) {
            throw new RuntimeException("保存附件文件失败: " + fileName, e);
        }
    }
}