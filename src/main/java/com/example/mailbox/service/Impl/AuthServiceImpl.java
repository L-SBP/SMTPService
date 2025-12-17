package com.example.mailbox.service.Impl;

import com.example.mailbox.dto.RegisterRequestDTO;
import com.example.mailbox.entity.Account;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.AuthService;
import com.example.mailbox.service.TokenService;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.vo.LoginResponseVO;
import com.example.mailbox.vo.UserInfoVO;
import org.springframework.beans.factory.annotation.Autowired;
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
        user.setPassword(registerRequestDTO.getPassword()); // 不再加密

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
        Optional<Account> account = userRepository.findByIdentifier(identify);

        if (account.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }
        Account user = account.get();

        if (!password.equals(user.getPassword())) { // 不再使用加密比较
            throw new RuntimeException("密码错误");
        }

        String token = jwtUtil.generateToken(user.getUsername());

        // 将Token存储到Redis
        long expiration = jwtUtil.getExpiration();
        tokenService.storeToken(token, user.getUsername(), expiration);

        // 构建用户信息
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setId(user.getId());
        userInfoVO.setUsername(user.getUsername());
        userInfoVO.setEmail(user.getEmail());
        userInfoVO.setSignature(user.getSignature());
        userInfoVO.setIsAdmin(user.getIsAdmin());
        userInfoVO.setQuotaLimit(user.getQuotaLimit());
        userInfoVO.setUsedSpace(user.getUsedSpace());
        userInfoVO.setLastLogin(user.getLastLogin());

        // 构建响应
        LoginResponseVO response = new LoginResponseVO();
        response.setToken(token);
        response.setUser(userInfoVO);

        return response;
    }
}
