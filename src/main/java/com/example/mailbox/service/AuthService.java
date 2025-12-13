package com.example.mailbox.service;

import com.example.mailbox.dto.AuthRequestDTO;
import com.example.mailbox.dto.RegisterRequestDTO;
import com.example.mailbox.entity.Account;
import com.example.mailbox.vo.AuthResponseVO;

public interface AuthService {
    AuthResponseVO register(RegisterRequestDTO registerRequestDTO);

    Account login(String email, String password);
}
