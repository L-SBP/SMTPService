package com.example.mailbox.dto;

import lombok.Data;

@Data
public class AttachmentDTO {
    private Long id;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String filePath;
}
