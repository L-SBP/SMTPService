package com.example.mailbox.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {
    private Long id;
    private String username;
    private String email;
    private String signature;
    private Boolean isAdmin;
    private Double quotaLimit;
    private Double usedSpace;
    private LocalDateTime lastLogin;
}
