package com.example.mailbox.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT 工具类 - 用于生成和验证 JWT 令牌
 * 替代 Spring Security 的认证机制
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Getter
    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * 从令牌中提取用户名
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 从令牌中提取过期时间
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * 从令牌中提取指定声明
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 解析所有声明
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("JWT 令牌已过期");
            throw new RuntimeException("JWT 令牌已过期", e);
        } catch (UnsupportedJwtException e) {
            log.warn("JWT 令牌格式不支持");
            throw new RuntimeException("JWT 令牌格式不支持", e);
        } catch (MalformedJwtException e) {
            log.warn("JWT 令牌格式错误");
            throw new RuntimeException("JWT 令牌格式错误", e);
        } catch (SignatureException e) {
            log.warn("JWT 令牌签名验证失败");
            throw new RuntimeException("JWT 令牌签名验证失败", e);
        } catch (IllegalArgumentException e) {
            log.warn("JWT 令牌参数不合法");
            throw new RuntimeException("JWT 令牌参数不合法", e);
        }
    }

    /**
     * 判断令牌是否过期
     */
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * 生成 JWT 令牌
     */
    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, username);
    }

    /**
     * 创建 JWT 令牌
     */
    private String createToken(Map<String, Object> claims, String subject) {
        try {
            return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(subject)
                    .setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + expiration))
                    .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                    .compact();
        } catch (Exception e) {
            log.error("生成 JWT 令牌失败", e);
            throw new RuntimeException("生成 JWT 令牌失败", e);
        }
    }

    /**
     * 验证 JWT 令牌
     */
    public Boolean validateToken(String token, String username) {
        try {
            final String extractedUsername = extractUsername(token);
            return (extractedUsername.equals(username) && !isTokenExpired(token));
        } catch (Exception e) {
            log.warn("验证 JWT 令牌失败", e);
            return false;
        }
    }

    /**
     * 检查令牌是否有效（不验证用户名）
     */
    public Boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.warn("令牌无效", e);
            return false;
        }
    }
}
