
package com.example.mailbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置类
 * 配置CSRF保护和其他安全设置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF保护，解决登录接口403问题
            .csrf(csrf -> csrf.disable())
            // 配置请求授权规则
            .authorizeHttpRequests(auth -> auth
                // 允许所有登录和注册请求
                .requestMatchers("/api/auth/**").permitAll()
                // 其他请求需要认证
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
