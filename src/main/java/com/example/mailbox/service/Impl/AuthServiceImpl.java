package com.example.mailbox.service.Impl;

import com.example.mailbox.dto.AuthRequestDTO;
import com.example.mailbox.dto.RegisterRequestDTO;
import com.example.mailbox.entity.Account;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.AuthService;
import com.example.mailbox.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.mailbox.vo.AuthResponseVO;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public AuthResponseVO register(RegisterRequestDTO registerRequestDTO) {
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(registerRequestDTO.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 创建新用户
        Account user = new Account();
        user.setUsername(registerRequestDTO.getEmail());
        user.setEmail(registerRequestDTO.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequestDTO.getPassword()));

        // 保存用户
        Account savedUser = userRepository.save(user);

        // 生成JWT Token
        String token = jwtUtil.generateToken(savedUser.getEmail());

        // 返回响应
        AuthResponseVO response = new AuthResponseVO();
        response.setEmail(savedUser.getEmail());
        response.setResult(true);

        return response;
    }

    @Override
    public Account login(String username, String password) {
        try {
            // 验证用户凭据
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 获取用户信息
            return userService.getUserByEmail(username);
        } catch (Exception e) {
            throw new RuntimeException("登录失败: " + e.getMessage());
        }
    }
}
