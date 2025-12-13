package com.example.mailbox.service;

import com.example.mailbox.entity.Account;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService {
    Account getUserByEmail(String email);
    Account save(Account user);

    // 修改密码接口
    void changePassword(String email, String oldPassword, String newPassword);
}