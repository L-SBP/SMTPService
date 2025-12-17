package com.example.mailbox.controller;

import com.example.mailbox.dto.AttachmentDTO;
import com.example.mailbox.entity.Attachment;
import com.example.mailbox.service.AttachmentService;
import com.example.mailbox.vo.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    @Autowired
    private AttachmentService attachmentService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<List<AttachmentDTO>>> uploadAttachments(
            @RequestParam("files") MultipartFile[] files) {
        try {
            List<AttachmentDTO> uploadedAttachments = new ArrayList<>();
            for (MultipartFile file : files) {
                // Pass null for emailId as it's not linked yet
                Attachment attachment = attachmentService.uploadAttachment(null, file);
                
                AttachmentDTO dto = new AttachmentDTO();
                dto.setId(attachment.getId());
                dto.setFileName(attachment.getFileName());
                dto.setFileSize(attachment.getFileSize());
                dto.setContentType(attachment.getContentType());
                dto.setFilePath(attachment.getFilePath());
                uploadedAttachments.add(dto);
            }
            return ResponseEntity.ok(new ApiResponse<>(true, uploadedAttachments, "上传成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "上传失败: " + e.getMessage(), null));
        }
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        Attachment attachment = attachmentService.getAttachment(id);
        if (attachment == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path filePath = Paths.get(attachment.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                String contentType = attachment.getContentType();
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
