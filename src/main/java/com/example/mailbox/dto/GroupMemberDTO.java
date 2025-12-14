package com.example.mailbox.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupMemberDTO {
    @NotNull(message = "群组ID不能为空")
    private Long groupId;

    @NotNull(message = "用户账户ID不能为空")
    private Long accountId;
}
