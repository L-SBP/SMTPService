package com.example.mailbox.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseVO {
    private String token;  // JWT Token
    private UserInfoVO user;  // 用户信息
}
