# Android邮箱项目 - 自定义SMTP/POP3服务器使用指南

## 📋 目录

- [项目概述](#项目概述)
- [服务器配置](#服务器配置)
- [如何使用自定义服务器](#如何使用自定义服务器)
- [Android客户端集成](#android客户端集成)
- [测试示例](#测试示例)
- [常见问题](#常见问题)

## 🎯 项目概述

本项目实现了完全自定义的SMTP和POP3邮件服务器，支持JWT认证，用于Android邮箱应用的邮件收发功能。

### 核心特性

- ✅ **自定义SMTP服务器** (端口25)
- ✅ **自定义POP3服务器** (端口110)
- ✅ **JWT认证机制**
- ✅ **Redis Token管理**
- ✅ **Android客户端示例**

## 🖥️ 服务器配置

### 端口配置

| 服务 | 端口 | 协议 | 认证方式 |
|------|------|------|----------|
| SMTP | 25 | SMTP | JWT |
| POP3 | 110 | POP3 | JWT |

### Redis配置

```properties
# application.properties
spring.redis.host=127.0.0.1
spring.redis.port=6379
spring.redis.password=
spring.redis.database=0
```

### JWT配置

```properties
# application.properties
jwt.secret=ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ123456
jwt.expiration=86400000  # 24小时
```

## 🚀 如何使用自定义服务器

### 1. 启动服务器

```bash
# 启动Spring Boot应用
mvn spring-boot:run

# 或者打包后运行
mvn clean package
java -jar target/mailbox-backend.jar
```

### 2. 服务器启动日志

启动成功后，你会看到类似日志：

```
自定义SMTP服务器启动，监听端口：0.0.0.0:25
自定义POP3服务器启动，监听端口：0.0.0.0:110
```

### 3. 测试连接

使用提供的测试客户端：

```bash
# 测试SMTP服务器
java -cp target/classes com.example.mailbox.util.SmtpClientExample

# 测试POP3服务器
java -cp target/classes com.example.mailbox.util.Pop3ClientExample
```

## 📱 Android客户端集成

### 1. 添加网络权限

在 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 2. 在子线程中使用

**发送邮件示例**：

```java
// 在子线程中执行
ExecutorService executor = Executors.newSingleThreadExecutor();

executor.execute(() -> {
    // 1. 用户登录获取JWT
    String jwtToken = loginAndGetJwt("user@mb.com", "password");
    
    // 2. 创建SMTP客户端
    AndroidSmtpClient smtpClient = new AndroidSmtpClient(
        "your-server-ip", 25, jwtToken
    );
    
    // 3. 测试连接
    boolean connected = smtpClient.testConnection();
    
    // 4. 发送邮件
    boolean sent = smtpClient.sendEmail(
        "user@mb.com",
        "target@mb.com", 
        "测试邮件",
        "这是一封来自Android应用的测试邮件！"
    );
    
    // 5. 回到主线程处理结果
    runOnUiThread(() -> {
        if (sent) {
            Toast.makeText(context, "邮件发送成功！", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "邮件发送失败！", Toast.LENGTH_SHORT).show();
        }
    });
});
```

**接收邮件示例**：

```java
executor.execute(() -> {
    // 1. 用户登录获取JWT
    String jwtToken = loginAndGetJwt("user@mb.com", "password");
    
    // 2. 创建POP3客户端
    AndroidPop3Client pop3Client = new AndroidPop3Client(
        "your-server-ip", 110, jwtToken
    );
    
    // 3. 连接服务器
    boolean connected = pop3Client.connect();
    
    // 4. 认证登录
    boolean authenticated = pop3Client.authenticate();
    
    // 5. 获取邮件列表
    List<AndroidPop3Client.EmailInfo> emails = pop3Client.listEmails();
    
    // 6. 读取第一封邮件
    if (!emails.isEmpty()) {
        String content = pop3Client.retrieveEmail(emails.get(0).getMessageId());
        // 处理邮件内容
    }
    
    // 7. 退出
    pop3Client.quit();
    
    // 8. 回到主线程
    runOnUiThread(() -> {
        // 更新UI
        updateEmailList(emails);
    });
});
```

### 3. JWT Token获取

通过登录接口获取JWT Token：

```java
// 登录接口
@PostMapping("/api/auth/login")
public LoginResponseVO login(@RequestBody AuthRequestDTO request) {
    // 返回包含JWT的响应
    return authService.login(request.getIdentifier(), request.getPassword());
}
```

响应示例：
```json
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "email": "user@mb.com",
    "result": true
}
```

## 🧪 测试示例

### SMTP测试流程

```
客户端 → 服务器：EHLO android-app
服务器 → 客户端：250 Hello android-app, pleased to meet you

客户端 → 服务器：AUTH JWT
服务器 → 客户端：334 Send JWT token

客户端 → 服务器：eyJhbGciOiJIUzI1NiJ9...
服务器 → 客户端：235 Authentication successful

客户端 → 服务器：MAIL FROM:<user@mb.com>
服务器 → 客户端：250 Ok

客户端 → 服务器：RCPT TO:<target@mb.com>
服务器 → 客户端：250 Ok

客户端 → 服务器：DATA
服务器 → 客户端：354 Enter message, ending with '.' on a line by itself

客户端 → 服务器：
From: user@mb.com
To: target@mb.com
Subject: Test Email

This is a test email.
.

服务器 → 客户端：250 Ok: queued

客户端 → 服务器：QUIT
服务器 → 客户端：221 Bye
```

### POP3测试流程

```
服务器 → 客户端：+OK POP3 Server Ready (Course Design)

客户端 → 服务器：AUTH JWT
服务器 → 客户端：+OK Send JWT token

客户端 → 服务器：eyJhbGciOiJIUzI1NiJ9...
服务器 → 客户端：+OK JWT authenticated, welcome user@mb.com

客户端 → 服务器：LIST
服务器 → 客户端：+OK 2 messages (3200 bytes)
服务器 → 客户端：1 1500
服务器 → 客户端：2 1700
服务器 → 客户端：.

客户端 → 服务器：RETR 1
服务器 → 客户端：+OK 1500 octets
服务器 → 客户端：From: sender@mb.com
服务器 → 客户端：To: user@mb.com
服务器 → 客户端：Subject: Test Email
服务器 → 客户端：
服务器 → 客户端：This is a test email.
服务器 → 客户端：.
```

## ❓ 常见问题

### Q1: 连接被拒绝？

**原因**：服务器未启动或端口被防火墙阻止

**解决**：
1. 确保Spring Boot应用已启动
2. 检查防火墙是否开放25和110端口
3. 确认服务器IP地址正确

### Q2: JWT认证失败？

**原因**：JWT无效或已过期

**解决**：
1. 重新登录获取新的JWT Token
2. 检查JWT密钥配置是否正确
3. 确认JWT未过期

### Q3: Android应用无法连接？

**原因**：网络权限或线程问题

**解决**：
1. 确认已添加网络权限
2. 确保在子线程中执行网络操作
3. 检查服务器IP地址是否正确

### Q4: 如何测试本地开发？

**方法1：使用10.0.2.2（Android模拟器）**

```java
// Android模拟器访问本地服务器
AndroidSmtpClient client = new AndroidSmtpClient(
    "10.0.2.2", 25, jwtToken
);
```

**方法2：使用真实设备**

```java
// 使用电脑的局域网IP
AndroidSmtpClient client = new AndroidSmtpClient(
    "192.168.1.100", 25, jwtToken
);
```

### Q5: 如何启用SSL/TLS？

目前服务器支持明文传输，生产环境建议：

1. 配置SSL证书
2. 使用端口465（SMTPS）和995（POP3S）
3. 修改客户端代码支持SSL连接

## 🔧 服务器架构

```
src/main/java/com/example/mailbox/config/
├── smtp/
│   └── SmtpServerConfig.java              # 自定义SMTP服务器（端口25）
│       ├── startCustomSmtpServer()        # 启动自定义SMTP服务器
│       └── SmtpClientHandler              # SMTP客户端处理器
└── pop3/
    └── Pop3ServerConfig.java              # 自定义POP3服务器（端口110）
        ├── startCustomPop3Server()        # 启动自定义POP3服务器
        └── Pop3ClientHandler              # POP3客户端处理器
```

## 📞 技术支持

如有问题，请联系开发团队或提交Issue。

---

**版本**: v5.0 (完全自定义服务器版)
**最后更新**: 2024年12月
