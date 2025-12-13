package com.example.mailbox.controller;

import com.example.mailbox.service.AttachmentService;
import com.example.mailbox.vo.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/emails/attachments")
public class AttachmentController {

    @Autowired
    private AttachmentService attachmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<String>> uploadAttachment(@RequestParam("emailId") Long emailId,
                                                                @RequestParam("attachment") MultipartFile file) {
        try {
            attachmentService.uploadAttachment(emailId, file);
            ApiResponse<String> response = new ApiResponse<>(
                true, "附件上传成功", "附件上传成功", null
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<String> response = new ApiResponse<>(
                false, null, "附件上传失败: " + e.getMessage(), null
            );
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/multi")
    public ResponseEntity<ApiResponse<String>> uploadMultipleAttachments(@RequestParam("emailId") Long emailId,
                                                                          @RequestParam("attachments") MultipartFile[] files) {
        try {
            attachmentService.uploadMultipleAttachments(emailId, files);
            ApiResponse<String> response = new ApiResponse<>(
                true, "多个附件上传成功", "多个附件上传成功", null
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<String> response = new ApiResponse<>(
                false, null, "附件上传失败: " + e.getMessage(), null
            );
            return ResponseEntity.badRequest().body(response);
        }
    }
}
