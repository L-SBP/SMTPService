package com.example.mailbox.service;

import com.example.mailbox.entity.Account;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService {
    Account getUserByEmail(String email);
    Account save(Account user);
}
