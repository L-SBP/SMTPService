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
    public Attachment uploadAttachment(Long emailId, MultipartFile file) {
        Email email = null;
        if (emailId != null) {
            email = emailRepository.findById(emailId)
                    .orElseThrow(() -> new RuntimeException("邮件不存在"));
        }

        try {
            if (email != null) {
                return saveFile(email, file.getInputStream(), file.getContentType(), file.getOriginalFilename(), file.getSize());
            } else {
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                String savedFileName = java.util.UUID.randomUUID() + "_" + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
                Path filePath = uploadPath.resolve(savedFileName);
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                Attachment attachment = new Attachment();
                attachment.setEmail(null);
                attachment.setFileName(file.getOriginalFilename());
                attachment.setFileSize(file.getSize());
                attachment.setContentType(file.getContentType());
                // 使用绝对路径
                attachment.setFilePath(filePath.toAbsolutePath().toString());
                // 注意：未绑定邮件时不持久化，只返回用于后续发送的临时信息
                return attachment;
            }
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

    @Override
    public Attachment getAttachment(Long id) {
        return attachmentRepository.findById(id).orElse(null);
    }

    private Attachment saveFile(Email email, InputStream inputStream, String contentType, String fileName, long size) {
        // 1. 检查并更新配额 (如果 email 不为空)
        if (email != null && email.getUser() != null) {
            Account user = email.getUser();
            // 将字节转换为MB
            double sizeInMb = size / (1024.0 * 1024.0);

            // 只有当用户有配额限制时才检查
            if (user.getQuotaLimit() > 0 && (user.getUsedSpace() + sizeInMb > user.getQuotaLimit())) {
                throw new RuntimeException("邮箱空间已满，无法保存附件: " + fileName);
            }
            
            // 更新用户已用空间
            user.setUsedSpace(user.getUsedSpace() + sizeInMb);
            userRepository.save(user);
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
            // 使用绝对路径以避免相对路径在不同执行环境下找不到文件的问题
            attachment.setFilePath(filePath.toAbsolutePath().toString());
            return attachmentRepository.save(attachment);

        } catch (IOException e) {
            throw new RuntimeException("保存附件文件失败: " + fileName, e);
        }
    }
}
