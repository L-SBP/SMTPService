package com.example.mailbox.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberVO {
    private Long groupId;  // 群组ID
    private Long accountId;  // 用户账户ID
    private LocalDateTime joinedAt;  // 加入时间
}
