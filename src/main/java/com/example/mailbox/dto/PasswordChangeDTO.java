package com.example.mailbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeDTO {
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;  // 原密码

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "新密码长度必须在6-100个字符之间")
    private String newPassword;  // 新密码

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;  // 确认密码
}
