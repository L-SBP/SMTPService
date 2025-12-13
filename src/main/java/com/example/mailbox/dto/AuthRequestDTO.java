package com.example.mailbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequestDTO {
    @NotBlank(message = "用户名或邮箱不能为空")
    @Size(min = 4, max = 50, message = "用户名长度必须在4-20个字符之间，邮箱长度不能超过50个字符")
    private String identifier; // 可以是用户名或邮箱

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100个字符之间")
    private String password;
}
