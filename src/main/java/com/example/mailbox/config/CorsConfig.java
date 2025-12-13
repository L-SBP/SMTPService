package com.example.mailbox.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 设置允许的源
        if ("*".equals(allowedOrigins)) {
            config.setAllowCredentials(true);
            config.addAllowedOriginPattern("*");
        } else {
            String[] origins = allowedOrigins.split(",");
            for (String origin : origins) {
                config.addAllowedOrigin(origin.trim());
            }
            config.setAllowCredentials(true);
        }
        
        // 设置允许的请求方法
        config.addAllowedMethod("*");
        
        // 设置允许的请求头
        config.addAllowedHeader("*");
        
        // 设置允许的响应头
        config.addExposedHeader("*");
        
        // 设置预检请求的缓存时间（秒）
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
