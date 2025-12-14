package com.example.mailbox.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EmailRequestDTO {
    @NotEmpty(message = "收件人不能为空")
    @Email(message = "收件人邮箱格式不正确")
    private List<String> to;  // 收件人列表

    @Email(message = "抄送人邮箱格式不正确")
    private List<String> cc;  // 抄送人列表

    @Email(message = "密送人邮箱格式不正确")
    private List<String> bcc;  // 密送人列表

    @NotBlank(message = "邮件主题不能为空")
    @Size(max = 500, message = "邮件主题不能超过500个字符")
    private String subject;  // 邮件主题

    private String body;  // 邮件正文

    private List<AttachmentDTO> attachments;  // 附件列表
}

@Data
class AttachmentDTO {
    private Long id;
    private String fileName;
    private Long fileSize;
    private String contentType;
}
