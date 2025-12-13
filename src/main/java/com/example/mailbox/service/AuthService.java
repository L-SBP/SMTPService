package com.example.mailbox.service;

import com.example.mailbox.dto.RegisterRequestDTO;
import com.example.mailbox.entity.Account;
import com.example.mailbox.vo.LoginResponseVO;
import com.example.mailbox.vo.RegisterResponseVO;

public interface AuthService {
    RegisterResponseVO register(RegisterRequestDTO registerRequestDTO);

    LoginResponseVO login(String email, String password);
}
