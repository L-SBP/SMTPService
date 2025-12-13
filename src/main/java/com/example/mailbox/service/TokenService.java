package com.example.mailbox.service;

import org.springframework.stereotype.Service;

/**
 * Token服务接口
 * 负责Token的存储和管理
 */
@Service
public interface TokenService {
    
    /**
     * 存储Token到Redis
     * @param token JWT Token
     * @param username 用户名
     * @param expiration 过期时间（毫秒）
     */
    void storeToken(String token, String username, long expiration);
    
    /**
     * 根据Token获取用户名
     * @param token JWT Token
     * @return 用户名，如果Token不存在或已过期返回null
     */
    String getUsernameByToken(String token);
    
    /**
     * 根据用户名获取Token
     * @param username 用户名
     * @return Token，如果不存在返回null
     */
    String getTokenByUsername(String username);
    
    /**
     * 删除Token
     * @param token JWT Token
     */
    void deleteToken(String token);
    
    /**
     * 删除用户的所有Token
     * @param username 用户名
     */
    void deleteTokensByUsername(String username);
    
    /**
     * 检查Token是否存在
     * @param token JWT Token
     * @return 是否存在
     */
    boolean hasToken(String token);
    
    /**
     * 刷新Token的过期时间
     * @param token JWT Token
     * @param expiration 新的过期时间（毫秒）
     */
    void refreshTokenExpiration(String token, long expiration);
}
