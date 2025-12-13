package com.example.mailbox.controller;

import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.Email.FolderType;
import com.example.mailbox.service.EmailService;
import com.example.mailbox.vo.ApiResponse;
import com.example.mailbox.vo.Page;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

    @Autowired
    private EmailService emailService;

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<Page<Email>>> getInbox(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Pageable pageable = PageRequest.of(page, size);
        Page<Email> emails = emailService.getInbox(email, pageable);

        ApiResponse<Page<Email>> response = new ApiResponse<>(
            true, emails, "获取收件箱邮件成功", null
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<Page<Email>>> getSent(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Pageable pageable = PageRequest.of(page, size);
        Page<Email> emails = emailService.getSent(email, pageable);

        ApiResponse<Page<Email>> response = new ApiResponse<>(
            true, emails, "获取已发送邮件成功", null
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Email>> getEmail(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Email emailDetails = emailService.getEmailById(id, email);

        ApiResponse<Email> response = new ApiResponse<>(
            true, emailDetails, "获取邮件详情成功", null
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<String>> sendEmail(@RequestBody EmailRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String senderEmail = authentication.getName();

        emailService.sendEmail(senderEmail, request);

        ApiResponse<String> response = new ApiResponse<>(
            true, "邮件发送成功", "邮件发送成功", null
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<String>> markAsRead(@PathVariable Long id,
                                                           @RequestBody Boolean isRead) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        emailService.markAsRead(id, email, isRead);

        ApiResponse<String> response = new ApiResponse<>(
            true, "标记已读成功", "标记已读成功", null
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/star")
    public ResponseEntity<ApiResponse<String>> markAsStarred(@PathVariable Long id,
                                                              @RequestBody Boolean isStarred) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        emailService.markAsStarred(id, email, isStarred);

        ApiResponse<String> response = new ApiResponse<>(
            true, "标记星标成功", "标记星标成功", null
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteEmail(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        emailService.deleteEmail(id, email);

        ApiResponse<String> response = new ApiResponse<>(
            true, "邮件删除成功", "邮件删除成功", null
        );

        return ResponseEntity.ok(response);
    }

    // 邮件请求DTO
    @AllArgsConstructor
    public static class EmailRequest {
        private List<String> to;
        private List<String> cc;
        private List<String> bcc;
        private String subject;
        private String body;
        private List<AttachmentRequest> attachments;
    }

    // 附件请求DTO
    @AllArgsConstructor
    public static class AttachmentRequest {
        private String fileName;
        private Long fileSize;
        private String contentType;
    }
}
