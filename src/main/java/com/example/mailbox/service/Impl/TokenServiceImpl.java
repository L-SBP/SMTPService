package com.example.mailbox.service.Impl;

import com.example.mailbox.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Token服务实现类
 * 使用Redis存储和管理Token
 */
@Service
public class TokenServiceImpl implements TokenService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // Redis Key前缀
    private static final String TOKEN_PREFIX = "token:";
    private static final String USERNAME_PREFIX = "username:";

    @Override
    public void storeToken(String token, String username, long expiration) {
        // 存储 token -> username 映射
        stringRedisTemplate.opsForValue().set(
            TOKEN_PREFIX + token, 
            username, 
            expiration, 
            TimeUnit.MILLISECONDS
        );
        
        // 存储 username -> token 映射（用于登出时删除）
        stringRedisTemplate.opsForValue().set(
            USERNAME_PREFIX + username, 
            token, 
            expiration, 
            TimeUnit.MILLISECONDS
        );
    }

    @Override
    public String getUsernameByToken(String token) {
        return stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
    }

    @Override
    public String getTokenByUsername(String username) {
        return stringRedisTemplate.opsForValue().get(USERNAME_PREFIX + username);
    }

    @Override
    public void deleteToken(String token) {
        String username = getUsernameByToken(token);
        if (username != null) {
            // 删除 token -> username 映射
            stringRedisTemplate.delete(TOKEN_PREFIX + token);
            // 删除 username -> token 映射
            stringRedisTemplate.delete(USERNAME_PREFIX + username);
        }
    }

    @Override
    public void deleteTokensByUsername(String username) {
        String token = getTokenByUsername(username);
        if (token != null) {
            // 删除 token -> username 映射
            stringRedisTemplate.delete(TOKEN_PREFIX + token);
            // 删除 username -> token 映射
            stringRedisTemplate.delete(USERNAME_PREFIX + username);
        }
    }

    @Override
    public boolean hasToken(String token) {
        return stringRedisTemplate.hasKey(TOKEN_PREFIX + token);
    }

    @Override
    public void refreshTokenExpiration(String token, long expiration) {
        // 检查token是否存在
        if (hasToken(token)) {
            // 获取用户名
            String username = getUsernameByToken(token);
            if (username != null) {
                // 刷新两个映射的过期时间
                stringRedisTemplate.expire(TOKEN_PREFIX + token, expiration, TimeUnit.MILLISECONDS);
                stringRedisTemplate.expire(USERNAME_PREFIX + username, expiration, TimeUnit.MILLISECONDS);
            }
        }
    }
}
