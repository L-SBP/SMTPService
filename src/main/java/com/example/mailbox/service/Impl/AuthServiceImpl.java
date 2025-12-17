package com.example.mailbox.service.Impl;

import com.example.mailbox.dto.RegisterRequestDTO;
import com.example.mailbox.entity.Account;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.AuthService;
import com.example.mailbox.service.TokenService;
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.vo.LoginResponseVO;
import com.example.mailbox.vo.UserInfoVO;
import com.example.mailbox.vo.RegisterResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenService tokenService;

    // 关键：注入密码加密器
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public RegisterResponseVO register(RegisterRequestDTO registerRequestDTO) {
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(registerRequestDTO.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 创建新用户
        Account user = new Account();
        user.setUsername(registerRequestDTO.getEmail()); // 默认用邮箱作为用户名
        user.setEmail(registerRequestDTO.getEmail());

        // 【关键修改】注册时必须加密密码
        user.setPassword(passwordEncoder.encode(registerRequestDTO.getPassword()));

        user.setIsAdmin(false); // 默认非管理员
        user.setEnabled(true);  // 默认启用
        user.setQuotaLimit(100.0); // 默认配额100MB
        user.setUsedSpace(0.0);

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
        // 支持用户名或邮箱登录
        Optional<Account> account = userRepository.findByIdentifier(identify);

        if (account.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }
        Account user = account.get();

        // 检查用户是否被禁用
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }

        // 【关键修改】登录必须使用 matches 方法比较明文和密文
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
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