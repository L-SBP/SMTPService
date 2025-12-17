package com.example.mailbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置类
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF保护
            .csrf(csrf -> csrf.disable())
            // 允许跨域
            .cors(cors -> cors.configure(http))
            // 配置请求授权规则
            .authorizeHttpRequests(auth -> auth
                // 允许所有登录和注册请求
                .requestMatchers("/api/auth/**").permitAll()
                // 允许根路径
                .requestMatchers("/").permitAll()
                .requestMatchers("/error").permitAll()
                // 允许Swagger文档
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // 允许H2控制台
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()

                // 【修改点】由于 Controller 内部实现了 Token 校验逻辑，这里暂时全部放行
                // 防止 Spring Security 因为没配置 JWT Filter 而直接拦截请求
                .anyRequest().permitAll()
            )
            // 允许H2控制台的iframe
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}