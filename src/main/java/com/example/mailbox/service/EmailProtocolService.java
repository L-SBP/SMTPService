package com.example.mailbox.service;

import com.example.mailbox.entity.Email;
import com.example.mailbox.vo.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 邮件协议服务接口
 * 负责使用POP3和SMTP协议与邮件服务器通信
 */
public interface EmailProtocolService {
    
    /**
     * 使用POP3协议接收邮件
     * @param email 用户邮箱地址
     * @param password 邮箱密码
     * @param host POP3服务器地址
     * @param port POP3服务器端口
     * @param ssl 是否使用SSL
     * @return 接收到的邮件列表
     */
    List<Email> receiveEmails(String email, String password, String host, int port, boolean ssl);
    
    /**
     * 使用SMTP协议发送邮件
     * @param senderEmail 发件人邮箱
     * @param password 发件人密码
     * @param host SMTP服务器地址
     * @param port SMTP服务器端口
     * @param ssl 是否使用SSL
     * @return 发送结果
     */
    boolean sendEmail(String senderEmail, String password, String host, int port, boolean ssl);
    
    /**
     * 获取POP3服务器配置
     * @param email 邮箱地址
     * @return 服务器配置
     */
    Pop3ServerConfig getPop3Config(String email);
    
    /**
     * 获取SMTP服务器配置
     * @param email 邮箱地址
     * @return 服务器配置
     */
    SmtpServerConfig getSmtpConfig(String email);
    
    /**
     * POP3服务器配置
     */
    class Pop3ServerConfig {
        private String host;
        private int port;
        private boolean ssl;
        
        public Pop3ServerConfig(String host, int port, boolean ssl) {
            this.host = host;
            this.port = port;
            this.ssl = ssl;
        }
        
        // Getters and Setters
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }
    }
    
    /**
     * SMTP服务器配置
     */
    class SmtpServerConfig {
        private String host;
        private int port;
        private boolean ssl;
        
        public SmtpServerConfig(String host, int port, boolean ssl) {
            this.host = host;
            this.port = port;
            this.ssl = ssl;
        }
        
        // Getters and Setters
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }
    }
}
