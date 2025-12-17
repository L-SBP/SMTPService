
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
            // 允许跨域
            .cors(cors -> cors.configure(http))
            // 配置请求授权规则
            .authorizeHttpRequests(auth -> auth
                // 允许所有登录和注册请求
                .requestMatchers("/api/auth/**").permitAll()
                // 允许根路径访问（用于验证服务是否启动）
                .requestMatchers("/").permitAll()
                .requestMatchers("/error").permitAll()
                // 允许Swagger文档
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // 允许H2控制台
                .requestMatchers("/h2-console/**").permitAll()
                // 允许Swagger文档
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                // 其他请求需要认证
                .anyRequest().authenticated()
            )
            // 允许H2控制台的iframe
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
