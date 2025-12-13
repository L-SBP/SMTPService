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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            // 创建上传目录
            Files.createDirectories(uploadPath);

            // 生成唯一文件名
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);

            // 保存文件
            file.transferTo(filePath.toFile());

            // 保存附件信息
            Attachment attachment = new Attachment();
            attachment.setEmail(email);
            attachment.setFileName(file.getOriginalFilename());
            attachment.setFileSize(file.getSize());
            attachment.setContentType(file.getContentType());
            attachment.setFilePath(filePath.toString());

            attachmentRepository.save(attachment);

        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public void uploadMultipleAttachments(Long emailId, MultipartFile[] files) {
        for (MultipartFile file : files) {
            uploadAttachment(emailId, file);
        }
    }
}
