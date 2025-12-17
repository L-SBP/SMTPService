package com.example.mailbox.service.Impl;

import com.example.mailbox.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
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

    private static final class TokenRecord {
        private final String username;
        private final long expiresAtMillis;

        private TokenRecord(String username, long expiresAtMillis) {
            this.username = username;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private final ConcurrentHashMap<String, TokenRecord> localTokenStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> localUsernameToToken = new ConcurrentHashMap<>();

    private TokenRecord getValidLocalRecord(String token) {
        TokenRecord record = localTokenStore.get(token);
        if (record == null) return null;
        if (System.currentTimeMillis() >= record.expiresAtMillis) {
            localTokenStore.remove(token);
            localUsernameToToken.remove(record.username, token);
            return null;
        }
        return record;
    }

    @Override
    public void storeToken(String token, String username, long expiration) {
        long expiresAtMillis = System.currentTimeMillis() + Math.max(0, expiration);
        localTokenStore.put(token, new TokenRecord(username, expiresAtMillis));
        localUsernameToToken.put(username, token);

        try {
            stringRedisTemplate.opsForValue().set(
                TOKEN_PREFIX + token,
                username,
                expiration,
                TimeUnit.MILLISECONDS
            );

            stringRedisTemplate.opsForValue().set(
                USERNAME_PREFIX + username,
                token,
                expiration,
                TimeUnit.MILLISECONDS
            );
        } catch (Exception ignored) {
        }
    }

    @Override
    public String getUsernameByToken(String token) {
        try {
            String username = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
            if (username != null) return username;
        } catch (Exception ignored) {
        }
        TokenRecord record = getValidLocalRecord(token);
        return record != null ? record.username : null;
    }

    @Override
    public String getTokenByUsername(String username) {
        try {
            String token = stringRedisTemplate.opsForValue().get(USERNAME_PREFIX + username);
            if (token != null) return token;
        } catch (Exception ignored) {
        }
        String token = localUsernameToToken.get(username);
        if (token == null) return null;
        return getValidLocalRecord(token) != null ? token : null;
    }

    @Override
    public void deleteToken(String token) {
        String username = null;
        TokenRecord record = localTokenStore.remove(token);
        if (record != null) {
            username = record.username;
            localUsernameToToken.remove(username, token);
        }

        if (username == null) {
            username = getUsernameByToken(token);
        }

        try {
            stringRedisTemplate.delete(TOKEN_PREFIX + token);
            if (username != null) {
                stringRedisTemplate.delete(USERNAME_PREFIX + username);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void deleteTokensByUsername(String username) {
        String token = localUsernameToToken.remove(username);
        if (token != null) {
            localTokenStore.remove(token);
        } else {
            token = getTokenByUsername(username);
        }

        try {
            if (token != null) {
                stringRedisTemplate.delete(TOKEN_PREFIX + token);
            }
            stringRedisTemplate.delete(USERNAME_PREFIX + username);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean hasToken(String token) {
        if (getValidLocalRecord(token) != null) return true;
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(TOKEN_PREFIX + token));
        } catch (Exception ignored) {
            return getValidLocalRecord(token) != null;
        }
    }

    @Override
    public void refreshTokenExpiration(String token, long expiration) {
        TokenRecord record = getValidLocalRecord(token);
        String username = record != null ? record.username : null;
        if (username == null) {
            username = getUsernameByToken(token);
        }

        if (username != null) {
            long expiresAtMillis = System.currentTimeMillis() + Math.max(0, expiration);
            localTokenStore.put(token, new TokenRecord(username, expiresAtMillis));
            localUsernameToToken.put(username, token);
        }

        try {
            stringRedisTemplate.expire(TOKEN_PREFIX + token, expiration, TimeUnit.MILLISECONDS);
            if (username != null) {
                stringRedisTemplate.expire(USERNAME_PREFIX + username, expiration, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) {
        }
    }
}
