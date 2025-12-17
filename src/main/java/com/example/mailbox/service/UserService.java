package com.example.mailbox.service;

import com.example.mailbox.entity.Account;
public interface UserService {
    Account getUserByEmail(String email);
    Account getUserById(Long id);
    Account save(Account user);

    // 修改密码接口
    void changePassword(String email, String oldPassword, String newPassword);
}
