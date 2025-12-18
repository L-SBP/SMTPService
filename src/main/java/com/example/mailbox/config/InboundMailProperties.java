package com.example.mailbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 入站邮件配置（从中继账号拉取邮件）
 * 用于从QQ/Gmail等外部邮箱拉取发送给系统用户的邮件
 */
@Data
@Component
@ConfigurationProperties(prefix = "inbound.mail")
public class InboundMailProperties {

  /**
   * 是否启用入站邮件拉取
   */
  private boolean enabled = false;

  /**
   * 邮件协议：pop3 或 imap
   */
  private String protocol = "imap";

  /**
   * 邮件服务器主机
   */
  private String host;

  /**
   * 邮件服务器端口
   * POP3: 110(普通)/995(SSL)
   * IMAP: 143(普通)/993(SSL)
   */
  private int port = 993;

  /**
   * 是否启用SSL
   */
  private boolean ssl = true;

  /**
   * 认证用户名（通常是邮箱地址）
   */
  private String username;

  /**
   * 认证密码（授权码）
   */
  private String password;

  /**
   * 拉取间隔（秒）
   */
  private int fetchInterval = 60;

  /**
   * 每次拉取的最大邮件数
   */
  private int maxFetchCount = 50;

  /**
   * 拉取后是否删除远程邮件
   */
  private boolean deleteAfterFetch = false;

  /**
   * 拉取后是否标记为已读
   */
  private boolean markAsReadAfterFetch = true;

  /**
   * 系统邮件域名（用于识别系统用户）
   * 例如：mb.com
   */
  private String systemDomain = "mb.com";

  /**
   * 默认收件人（当无法识别目标用户时）
   * 留空则丢弃无法识别的邮件
   */
  private String defaultRecipient;

  /**
   * 连接超时（毫秒）
   */
  private int connectionTimeout = 30000;

  /**
   * 读取超时（毫秒）
   */
  private int readTimeout = 60000;
}
