package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Account;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public Account getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    @Override
    public Account getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Override
    public Account save(Account user) {
        return userRepository.save(user);
    }


    // 3. 实现修改密码逻辑
    @Override
    public void changePassword(String email, String oldPassword, String newPassword) {
        Account user = getUserByEmail(email);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证旧密码是否正确
        if (!oldPassword.equals(user.getPassword())) {
            throw new RuntimeException("原密码不正确");
        }

        // 保存新密码
        user.setPassword(newPassword);
        userRepository.save(user);
    }
}
