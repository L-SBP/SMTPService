package com.example.mailbox.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailQueueStatusDTO {
    private Long id;
    private Long emailId;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String errorMessage;
    private int retryCount;
    private String status;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

