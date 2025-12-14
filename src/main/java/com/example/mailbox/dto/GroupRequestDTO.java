package com.example.mailbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GroupRequestDTO {
    private Long id;  // 群组ID（更新时使用）

    @NotBlank(message = "群组名称不能为空")
    @Size(max = 100, message = "群组名称不能超过100个字符")
    private String name;  // 群组名称

    @Size(max = 500, message = "群组描述不能超过500个字符")
    private String description;  // 群组描述
}
