package com.example.mailbox.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupVO {
    private Long id;
    private String name;  // 群组名称
    private String description;  // 群组描述
    private Integer memberCount;  // 成员数量
    private Long createdBy;  // 创建者ID
    private LocalDateTime createdDate;  // 创建日期
    private LocalDateTime lastModified;  // 最后修改日期
}
