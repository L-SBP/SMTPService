# Android邮箱项目后端

基于Spring Boot的邮箱服务后端，提供完整的用户认证、邮件管理、附件上传和群组管理功能。

## 技术栈

- **框架**: Spring Boot 3.x
- **认证**: Spring Security + JWT
- **数据访问**: Spring Data JPA
- **数据库**: H2 (开发), MySQL (生产)
- **缓存**: Redis (可选)
- **构建工具**: Maven

## 项目结构

```
src/main/java/com/example/mailbox/
├── controller/          # REST API控制器
│   ├── AuthController.java      # 用户认证接口
│   ├── UserController.java      # 用户管理接口
│   ├── EmailController.java     # 邮件管理接口
│   ├── AttachmentController.java # 附件上传接口
│   └── GroupController.java     # 群组管理接口
├── service/             # 业务逻辑层
│   ├── AuthService.java
│   ├── UserService.java
│   ├── EmailService.java
│   ├── AttachmentService.java
│   └── GroupService.java
├── service/Impl/        # 业务逻辑实现
├── repository/          # 数据访问层
├── entity/              # 实体类
├── dto/                 # 数据传输对象
├── vo/                  # 视图对象
├── config/              # 配置类
├── filter/              # 过滤器
├── util/                # 工具类
└── exception/           # 异常处理
```

## API接口

### 1. 用户认证接口 (`/api/auth`)

- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/logout` - 用户登出

### 2. 用户管理接口 (`/api/users`)

- `GET /api/users/profile` - 获取用户资料
- `PUT /api/users/profile` - 更新用户资料
- `PUT /api/users/password` - 修改密码

### 3. 邮件管理接口 (`/api/emails`)

- `GET /api/emails/inbox` - 获取收件箱邮件
- `GET /api/emails/sent` - 获取已发送邮件
- `GET /api/emails/{id}` - 获取邮件详情
- `POST /api/emails/send` - 发送邮件
- `PUT /api/emails/{id}/read` - 标记已读/未读
- `PUT /api/emails/{id}/star` - 标记星标/取消星标
- `DELETE /api/emails/{id}` - 删除邮件

### 4. 附件上传接口 (`/api/emails/attachments`)

- `POST /api/emails/attachments` - 上传单个附件
- `POST /api/emails/attachments/multi` - 上传多个附件

### 5. 群组管理接口 (`/api/groups`)

- `POST /api/groups` - 创建群组
- `PUT /api/groups` - 更新群组
- `DELETE /api/groups/{groupId}` - 删除群组
- `GET /api/groups/{groupId}` - 获取群组详情
- `GET /api/groups/created` - 获取用户创建的群组
- `GET /api/groups/joined` - 获取用户加入的群组
- `GET /api/groups/all` - 获取所有群组
- `GET /api/groups/search` - 搜索群组
- `POST /api/groups/members` - 添加群组成员
- `DELETE /api/groups/{groupId}/members/{accountId}` - 移除群组成员
- `GET /api/groups/{groupId}/members` - 获取群组成员列表
- `GET /api/groups/{groupId}/member-count` - 获取群组成员数量
- `GET /api/groups/{groupId}/members/{accountId}/check` - 检查用户是否为群组成员

## 数据库模型

### 用户表 (users)
- 用户ID、用户名、邮箱、密码、签名、管理员标志、存储配额、已用空间、最后登录时间

### 邮件表 (emails)
- 邮件ID、发件人、收件人、抄送、密送、主题、正文、附件标志、已读标志、星标标志、大小、接收时间、文件夹类型、用户ID

### 附件表 (attachments)
- 附件ID、邮件ID、文件名、文件大小、内容类型、文件路径、上传时间

### 群组表 (groups)
- 群组ID、名称、描述、成员数量、创建者ID、创建时间、最后修改时间

### 群组成员表 (group_members)
- ID、群组ID、账户ID、加入时间

## 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.6+
- MySQL 8.0+ (生产环境)

### 2. 开发环境启动

```bash
# 克隆项目
git clone <repository-url>
cd SMTPService

# 编译项目
mvn clean compile

# 启动应用（使用H2内存数据库）
mvn spring-boot:run

# 或者使用开发配置文件
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

应用启动后，访问：
- API文档: http://localhost:8080/h2-console (H2控制台)
- 应用接口: http://localhost:8080/api/*

### 3. 生产环境部署

```bash
# 打包应用
mvn clean package

# 运行JAR包
java -jar target/mailbox-backend-1.0.0.jar --spring.profiles.active=prod
```

## 配置说明

### 开发环境配置 (`application-dev.properties`)

- 使用H2内存数据库
- 启用SQL日志
- 允许所有CORS请求
- 文件上传到本地目录

### 生产环境配置 (`application-prod.properties`)

- 使用MySQL数据库
- 禁用SQL日志
- 限制CORS来源
- 文件上传到指定目录
- 启用Redis缓存

## 安全配置

- JWT Token认证
- 密码BCrypt加密
- CORS跨域配置
- CSRF保护已禁用（适用于API服务）

## 开发指南

### 添加新的API接口

1. 在 `controller` 包中创建控制器类
2. 在 `service` 包中定义服务接口
3. 在 `service/Impl` 包中实现服务逻辑
4. 在 `repository` 包中定义数据访问接口
5. 在 `entity` 包中定义实体类

### 数据库迁移

使用Spring Boot的 `spring.jpa.hibernate.ddl-auto` 配置：
- `create-drop`: 开发环境，每次启动重建表
- `validate`: 生产环境，验证表结构
- `update`: 自动更新表结构（不推荐生产使用）

## 测试

```bash
# 运行单元测试
mvn test

# 运行集成测试
mvn verify
```

## 监控和日志

- 日志文件: `logs/mailbox.log`
- 健康检查: `/actuator/health`
- 指标监控: `/actuator/metrics`

## 常见问题

### 1. 数据库连接失败

检查 `application.properties` 中的数据库配置，确保MySQL服务正在运行。

### 2. JWT Token无效

确保 `jwt.secret` 配置正确，生产环境请使用强密钥。

### 3. 文件上传失败

检查上传目录权限和磁盘空间。

## 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 联系方式

如有问题或建议，请提交 Issue 或联系开发团队。
