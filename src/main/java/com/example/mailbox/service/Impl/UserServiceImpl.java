package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Account;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @Override
    @Transactional
    public void changePassword(String email, String oldPassword, String newPassword) {
        // 1. 修改入参为 email，以匹配接口定义
        log.info("接收到修改密码请求，用户邮箱: {}", email);

        // 2. 通过邮箱查找用户
        Account user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 3. 校验旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.error("修改密码失败：旧密码不匹配");
            throw new RuntimeException("原密码错误");
        }

        // 4. 加密新密码并保存
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);

        userRepository.save(user);
        log.info("密码修改成功");
    }
}