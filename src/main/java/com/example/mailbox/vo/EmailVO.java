package com.example.mailbox.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailVO {
    private Long id;
    private String sender;  // 发件人
    private List<String> recipients;  // 收件人列表
    private List<String> cc;  // 抄送人列表
    private List<String> bcc;  // 密送人列表
    private String subject;  // 邮件主题
    private String body;  // 邮件正文
    private Boolean hasAttachment;  // 是否有附件
    private Boolean isRead;  // 是否已读
    private Boolean isStarred;  // 是否星标
    private Long size;  // 邮件大小
    private LocalDateTime receivedTime;  // 接收时间
    private String folderType;  // 文件夹类型
}
