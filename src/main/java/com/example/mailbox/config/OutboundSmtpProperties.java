package com.example.mailbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部SMTP（中继）配置。
 *
 * 典型用途：把系统内的邮件转发到 QQ/Gmail/企业邮箱等外部邮箱。
 */
@Component
@ConfigurationProperties(prefix = "outbound.smtp")
public class OutboundSmtpProperties {

  /** 是否启用外部SMTP中继 */
  private boolean enabled = false;

  /** 外部SMTP服务器地址，例如 smtp.qq.com */
  private String host;

  /** 外部SMTP端口：465(SSL) 或 587(STARTTLS) */
  private int port = 587;

  /** 是否使用SSL（通常端口465为true） */
  private boolean ssl = false;

  /** 是否使用STARTTLS（通常端口587为true） */
  private boolean starttls = true;

  /** 外部SMTP认证用户名（通常是邮箱地址） */
  private String username;

  /** 外部SMTP认证密码（常见为“授权码/应用专用密码”） */
  private String password;

  /**
   * 可选：强制覆盖 From 地址。
   * 一些SMTP服务商要求 From 必须与认证账号一致，否则会拒收。
   */
  private String fromOverride;

  /** 连接超时时间（毫秒），默认30秒 */
  private int connectionTimeout = 30000;

  /** 读取超时时间（毫秒），默认60秒 */
  private int timeout = 60000;

  /** 写入超时时间（毫秒），默认60秒 */
  private int writeTimeout = 60000;

  /** 是否开启调试模式 */
  private boolean debug = false;

  /** 默认发件人显示名称 */
  private String defaultDisplayName;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public boolean isSsl() {
    return ssl;
  }

  public void setSsl(boolean ssl) {
    this.ssl = ssl;
  }

  public boolean isStarttls() {
    return starttls;
  }

  public void setStarttls(boolean starttls) {
    this.starttls = starttls;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getFromOverride() {
    return fromOverride;
  }

  public void setFromOverride(String fromOverride) {
    this.fromOverride = fromOverride;
  }

  public int getConnectionTimeout() {
    return connectionTimeout;
  }

  public void setConnectionTimeout(int connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }

  public int getTimeout() {
    return timeout;
  }

  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }

  public int getWriteTimeout() {
    return writeTimeout;
  }

  public void setWriteTimeout(int writeTimeout) {
    this.writeTimeout = writeTimeout;
  }

  public boolean isDebug() {
    return debug;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  public String getDefaultDisplayName() {
    return defaultDisplayName;
  }

  public void setDefaultDisplayName(String defaultDisplayName) {
    this.defaultDisplayName = defaultDisplayName;
  }
}
