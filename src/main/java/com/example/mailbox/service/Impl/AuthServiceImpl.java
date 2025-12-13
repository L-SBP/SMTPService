package com.example.mailbox.service.Impl;

import com.example.mailbox.dto.RegisterRequestDTO;
import com.example.mailbox.entity.Account;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.AuthService;
import com.example.mailbox.service.TokenService;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.vo.LoginResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.mailbox.vo.RegisterResponseVO;

import java.util.Optional;

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

    @Autowired
    private TokenService tokenService;

    @Override
    public RegisterResponseVO register(RegisterRequestDTO registerRequestDTO) {
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

        // 返回响应
        RegisterResponseVO response = new RegisterResponseVO();
        response.setEmail(savedUser.getEmail());
        response.setResult(true);

        return response;
    }

    @Override
    public LoginResponseVO login(String identify, String password) {
        Optional<Account> accountOpt = userRepository.findByEmail(identify);

        if (accountOpt.isEmpty()) {
            accountOpt = userRepository.findByUsername(identify);
        }

        if (accountOpt.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }

        Account account = accountOpt.get();

        if (!passwordEncoder.matches(password, account.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        String token = jwtUtil.generateToken(account);

        // 将Token存储到Redis
        long expiration = jwtUtil.getExpiration();
        tokenService.storeToken(token, account.getUsername(), expiration);

        LoginResponseVO response = new LoginResponseVO();
        response.setToken(token);
        response.setEmail(account.getEmail());
        response.setResult(true);
        return response;
    }
}
