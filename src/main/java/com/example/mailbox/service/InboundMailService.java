package com.example.mailbox.service;

import com.example.mailbox.entity.Email;
import java.util.List;

/**
 * 入站邮件服务接口
 * 从中继账号拉取外部发送给系统用户的邮件
 */
public interface InboundMailService {

  /**
   * 检查入站邮件功能是否启用
   */
  boolean isEnabled();

  /**
   * 手动触发一次邮件拉取
   * 
   * @return 拉取到的邮件数量
   */
  int fetchEmails();

  /**
   * 获取拉取状态信息
   */
  FetchStatus getStatus();

  /**
   * 测试连接
   * 
   * @return 是否连接成功
   */
  boolean testConnection();

  /**
   * 获取配置状态
   */
  InboundConfigStatus getConfigStatus();

  /**
   * 拉取状态
   */
  class FetchStatus {
    private boolean enabled;
    private boolean running;
    private long lastFetchTime;
    private int lastFetchCount;
    private String lastError;
    private long totalFetched;

    // Getters and Setters
    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isRunning() {
      return running;
    }

    public void setRunning(boolean running) {
      this.running = running;
    }

    public long getLastFetchTime() {
      return lastFetchTime;
    }

    public void setLastFetchTime(long lastFetchTime) {
      this.lastFetchTime = lastFetchTime;
    }

    public int getLastFetchCount() {
      return lastFetchCount;
    }

    public void setLastFetchCount(int lastFetchCount) {
      this.lastFetchCount = lastFetchCount;
    }

    public String getLastError() {
      return lastError;
    }

    public void setLastError(String lastError) {
      this.lastError = lastError;
    }

    public long getTotalFetched() {
      return totalFetched;
    }

    public void setTotalFetched(long totalFetched) {
      this.totalFetched = totalFetched;
    }
  }

  /**
   * 入站配置状态
   */
  class InboundConfigStatus {
    private boolean enabled;
    private boolean configured;
    private String protocol;
    private String host;
    private int port;
    private boolean sslEnabled;
    private String username;
    private String systemDomain;
    private int fetchInterval;
    private String errorMessage;

    // Getters and Setters
    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isConfigured() {
      return configured;
    }

    public void setConfigured(boolean configured) {
      this.configured = configured;
    }

    public String getProtocol() {
      return protocol;
    }

    public void setProtocol(String protocol) {
      this.protocol = protocol;
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

    public boolean isSslEnabled() {
      return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
      this.sslEnabled = sslEnabled;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getSystemDomain() {
      return systemDomain;
    }

    public void setSystemDomain(String systemDomain) {
      this.systemDomain = systemDomain;
    }

    public int getFetchInterval() {
      return fetchInterval;
    }

    public void setFetchInterval(int fetchInterval) {
      this.fetchInterval = fetchInterval;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }
  }
}
