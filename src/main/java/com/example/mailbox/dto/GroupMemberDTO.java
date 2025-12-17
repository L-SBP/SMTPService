package com.example.mailbox.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroupMemberDTO {
    private Long id;            // 成员记录ID
    private Long groupId;       // 群组ID
    private Long accountId;     // 用户ID
    private String username;    // 用户名 (新增，用于前端显示)
    private String email;       // 邮箱 (新增，用于前端显示)
    private LocalDateTime joinedAt; // 加入时间
    private String role;        // 角色 (OWNER/MEMBER)
}