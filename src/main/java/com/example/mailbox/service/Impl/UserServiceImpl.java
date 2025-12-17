package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Email;
import com.example.mailbox.repository.EmailRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.EmailService;
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
    private EmailRepository emailRepository;

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

    @Override
    @Transactional
    public void markAsStarred(Long emailId, String email, Boolean isStarred) {
        log.info("标记邮件星标: emailId={}, email={}, isStarred={}", emailId, email, isStarred);
        
        // 1. 通过邮箱查找用户
        Account user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 2. 查找邮件
        Email emailEntity = emailRepository.findById(emailId)
            .orElseThrow(() -> new RuntimeException("邮件不存在"));

        // 3. 验证邮件是否属于该用户
        if (!emailEntity.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("无权限操作该邮件");
        }

        // 4. 更新星标状态
        emailEntity.setStarred(isStarred);
        emailRepository.save(emailEntity);
        
        log.info("邮件星标标记成功: emailId={}, isStarred={}", emailId, isStarred);
    }
}
