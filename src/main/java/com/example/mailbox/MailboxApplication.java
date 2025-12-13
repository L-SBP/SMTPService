package com.example.mailbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 邮箱应用主启动类
 * 
 * 使用融合后的自定义SMTP和POP3服务器（支持JWT认证）
 * 
 * 服务器配置说明：
 * - SMTP服务器：端口25，支持JWT认证，具备完整的MIME解析和附件处理能力
 * - POP3服务器：端口110，支持JWT认证，具备完整的MIME解析和附件处理能力
 * 
 * 服务器通过@Configuration注解自动启动，无需在Application中手动启动
 */
@SpringBootApplication
public class MailboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailboxApplication.class, args);
    }
}
