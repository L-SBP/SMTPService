package com.example.mailbox.controller;

import com.example.mailbox.dto.EmailRequestDTO;
import com.example.mailbox.entity.Email;
import com.example.mailbox.service.EmailService;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.vo.ApiResponse;
import com.example.mailbox.vo.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emails")
@Slf4j
public class EmailController {

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtUtil jwtUtil;

    private String getEmailFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("未提供认证信息");
        }
        String token = authHeader.substring(7);
        return jwtUtil.extractUsername(token);
    }

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<Page<Email>>> getInbox(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            String email = getEmailFromToken(request);
            Pageable pageable = PageRequest.of(page, size);
            Page<Email> result = emailService.getInbox(email, pageable);
            return ResponseEntity.ok(new ApiResponse<>(true, result, "获取收件箱成功", null));
        } catch (Exception e) {
            log.error("获取收件箱失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<Page<Email>>> getSent(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            String email = getEmailFromToken(request);
            Pageable pageable = PageRequest.of(page, size);
            Page<Email> result = emailService.getSent(email, pageable);
            return ResponseEntity.ok(new ApiResponse<>(true, result, "获取发件箱成功", null));
        } catch (Exception e) {
            log.error("获取发件箱失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }
    
    @GetMapping("/drafts")
    public ResponseEntity<ApiResponse<Page<Email>>> getDrafts(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            String email = getEmailFromToken(request);
            Pageable pageable = PageRequest.of(page, size);
            Page<Email> result = emailService.getDrafts(email, pageable);
            return ResponseEntity.ok(new ApiResponse<>(true, result, "获取草稿箱成功", null));
        } catch (Exception e) {
            log.error("获取草稿箱失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }
    
    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<Page<Email>>> getTrash(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            String email = getEmailFromToken(request);
            Pageable pageable = PageRequest.of(page, size);
            Page<Email> result = emailService.getTrash(email, pageable);
            return ResponseEntity.ok(new ApiResponse<>(true, result, "获取垃圾箱成功", null));
        } catch (Exception e) {
            log.error("获取垃圾箱失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }
    
    @GetMapping("/starred")
    public ResponseEntity<ApiResponse<Page<Email>>> getStarred(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            String email = getEmailFromToken(request);
            Pageable pageable = PageRequest.of(page, size);
            Page<Email> result = emailService.getStarred(email, pageable);
            return ResponseEntity.ok(new ApiResponse<>(true, result, "获取星标邮件成功", null));
        } catch (Exception e) {
            log.error("获取星标邮件失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<ApiResponse<Email>> getEmailDetail(
            HttpServletRequest request,
            @PathVariable Long id) {
        try {
            String email = getEmailFromToken(request);
            Email result = emailService.getEmailById(id, email);
            return ResponseEntity.ok(new ApiResponse<>(true, result, "获取邮件详情成功", null));
        } catch (Exception e) {
            log.error("获取邮件详情失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<String>> sendEmail(
            HttpServletRequest request,
            @RequestBody @jakarta.validation.Valid EmailRequestDTO emailRequest) {
        try {
            String email = getEmailFromToken(request);
            log.info("接收到发送邮件请求: sender={}, to={}, subject={}", email, emailRequest.getTo(), emailRequest.getSubject());
            
            emailService.sendEmail(
                email, 
                emailRequest.getTo(), 
                emailRequest.getSubject(), 
                emailRequest.getBody(),
                emailRequest.getAttachments() // 传递附件
            );
            
            return ResponseEntity.ok(new ApiResponse<>(true, "邮件发送成功", "邮件发送成功", null));
        } catch (Throwable e) {
            log.error("邮件发送失败", e);
            System.err.println("邮件发送异常详情: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "邮件发送失败: " + e.getMessage(), null));
        }
    }
    
    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<String>> saveDraft(
            HttpServletRequest request,
            @RequestBody EmailRequestDTO emailRequest) {
        try {
            String email = getEmailFromToken(request);
            emailService.saveDraft(email, emailRequest.getTo(), emailRequest.getSubject(), emailRequest.getBody());
            return ResponseEntity.ok(new ApiResponse<>(true, "草稿保存成功", "草稿保存成功", null));
        } catch (Exception e) {
            log.error("草稿保存失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "草稿保存失败: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<String>> markAsRead(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody Boolean isRead) {
        try {
            String email = getEmailFromToken(request);
            emailService.markAsRead(id, email, isRead);
            return ResponseEntity.ok(new ApiResponse<>(true, "标记成功", "标记成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    @PutMapping("/{id}/star")
    public ResponseEntity<ApiResponse<String>> markAsStarred(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody Boolean isStarred) {
        try {
            String email = getEmailFromToken(request);
            emailService.markAsStarred(id, email, isStarred);
            return ResponseEntity.ok(new ApiResponse<>(true, "标记成功", "标记成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteEmail(
            HttpServletRequest request,
            @PathVariable Long id) {
        try {
            String email = getEmailFromToken(request);
            emailService.deleteEmail(id, email);
            return ResponseEntity.ok(new ApiResponse<>(true, "删除成功", "删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }
}
