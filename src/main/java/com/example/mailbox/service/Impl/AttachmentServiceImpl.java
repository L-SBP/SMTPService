package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Attachment;
import com.example.mailbox.entity.Email;
import com.example.mailbox.repository.AttachmentRepository;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository; // 导入
import com.example.mailbox.service.AttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired private EmailRepository emailRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private UserRepository userRepository; // 注入用户Repo以更新配额

    private final Path uploadPath = Paths.get("uploads/attachments");

    @Override
    @Transactional
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
    @Transactional
    public void uploadMultipleAttachments(Long emailId, MultipartFile[] files) {
        for (MultipartFile file : files) {
            uploadAttachment(emailId, file);
        }
    }

    @Override
    @Transactional
    public void saveAttachmentFromStream(Email email, InputStream inputStream, String contentType, String fileName, long size) {
        saveFile(email, inputStream, contentType, fileName, size);
    }

    private void saveFile(Email email, InputStream inputStream, String contentType, String fileName, long size) {
        // 1. 检查并更新配额
        Account user = email.getUser();
        // 将字节转换为MB
        double sizeInMb = size / (1024.0 * 1024.0);

        // 只有当用户有配额限制时才检查
        if (user.getQuotaLimit() > 0 && (user.getUsedSpace() + sizeInMb > user.getQuotaLimit())) {
            throw new RuntimeException("邮箱空间已满，无法保存附件: " + fileName);
        }

        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String savedFileName = UUID.randomUUID() + "_" + (fileName != null ? fileName : "unknown");
            Path filePath = uploadPath.resolve(savedFileName);

            // 保存物理文件
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

            // 保存数据库记录
            Attachment attachment = new Attachment();
            attachment.setEmail(email);
            attachment.setFileName(fileName);
            attachment.setFileSize(size);
            attachment.setContentType(contentType);
            attachment.setFilePath(filePath.toString());
            attachmentRepository.save(attachment);

            // 2. 更新用户已用空间
            user.setUsedSpace(user.getUsedSpace() + sizeInMb);
            userRepository.save(user);

        } catch (IOException e) {
            throw new RuntimeException("保存附件文件失败: " + fileName, e);
        }
    }
}