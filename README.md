# LuckyFlow 后端

LuckyFlow 是一个营销抽奖平台的后端项目，覆盖用户认证、活动与奖池配置、抽奖、积分、奖励履约和中奖通知。当前采用 **Maven 多模块的模块化单体架构**，由 `luckyflow-app` 统一启动，其余模块提供领域能力。

本 README 对应项目后端代码，范围为后端源码、构建配置、运行资源、和必要接口文档。运行前需要另行准备匹配当前代码的数据库，MyBatis XML 与 Lua 均作为后端运行资源保留。

## 技术栈

| 技术 | 用途 |
| --- | --- |
| Java | 编译与运行；版本以根 `pom.xml` 为准 |
| Spring Boot 4.0.8 | Web 应用、配置与组件装配 |
| Maven | 多模块依赖管理与构建 |
| MyBatis-Plus 3.5.17 / MyBatis XML | 数据访问与自定义查询 |
| MySQL | 用户、活动、抽奖、履约和通知等业务数据 |
| Redis / Lua | 库存预留、抽奖次数控制、验证码及原子校验 |
| RabbitMQ / Spring AMQP | 中奖事件投递、消费、重试与死信处理 |
| Spring Security / JWT | 无状态身份认证及 USER / ADMIN 角色授权 |
| Spring Mail | 邮箱验证码与中奖邮件发送 |

## 目录结构

```text
.
├── pom.xml                         # Maven 聚合父工程
├── luckyflow-app/                   # 启动、HTTP API、安全、发布恢复、Outbox 与通知装配
├── luckyflow-common/                # 统一响应、分页、错误码和公共异常
├── luckyflow-user/                  # 用户、联系方式、积分账户与积分流水
├── luckyflow-marketing/             # 活动、规则、奖品目录、活动奖池与生命周期
├── luckyflow-lottery/               # 抽奖策略、资源预留、抽奖记录、Outbox 与恢复
├── luckyflow-fulfillment/           # 实物领奖、物流、虚拟奖励与积分奖励履约
└── docs/
    └── git-upload.md                # 仅上传后端必要文件的操作指南
```

上图展示后端的提交结构，各模块的 `src/main/java` 为业务源码，`src/main/resources` 中的 MyBatis XML、Redis Lua 与公共配置是运行资源。

## 已实现能力

- **用户与认证**：邮箱/手机验证码接口、注册、密码/验证码登录、JWT、本人资料、密码与已验证联系方式管理。
- **活动与奖品**：公开活动列表和详情、参与资格查询；管理端活动、规则、共享奖品与奖池维护；发布时冻结奖池快照、持久化发布意图，通过归属标记和定时恢复核对 Redis 库存，并推进活动生命周期。
- **抽奖**：权重策略、库存及次数预留、积分扣减、幂等抽奖、抽奖/中奖记录、原请求及未决请求查询、失败补偿与恢复。
- **奖励履约**：实物奖品领取、管理员发货、用户确认收货，以及虚拟奖品和积分奖励处理。
- **消息与通知**：Transactional Outbox 定时发布与超时恢复、RabbitMQ 发布确认、消费幂等、重试和死信处理；邮件发送与通知记录查询。
- **管理查询**：运营统计、用户与通知只读核查、安全配置状态查询。

抽奖流程可概括为：

```text
客户端携带 JWT 与 Idempotency-Key
    → 验证活动、用户资格和请求幂等性
    → Redis / Lua 预留库存及抽奖次数
    → MySQL 事务提交抽奖事实、积分及 Outbox 事件
    → Outbox 投递 RabbitMQ
    → 奖励履约与通知消费者分别处理
```

遇到未知结果时，应保留原 `Idempotency-Key`，通过原请求查询接口确认状态。网络超时后不应直接生成新键再次抽奖；只有服务端确认取消、资源恢复完成且允许重试后，才发起新请求。

## Redis Lua 脚本

| 目录 | 文件 | 用途 |
| --- | --- | --- |
| `luckyflow-lottery/src/main/resources/lua/` | `inventory-reserve.lua` | 原子预留奖品库存 |
| 同上 | `inventory-release.lua` | 幂等释放预留库存 |
| 同上 | `daily-draw-limit.lua` | 每日抽奖次数控制 |
| 同上 | `release-draw-time.lua` | 恢复预留的抽奖次数 |
| 同上 | `confirm-draw-reservation.lua` | 确认抽奖资源预留的成功终态 |
| 同上 | `inventory-init-owned.lua` | 发布活动时原子登记归属并初始化库存 |
| 同上 | `inventory-cleanup-owned.lua` | 按发布尝试的归属标记执行库存清理 |
| `luckyflow-app/src/main/resources/auth/` | `verification-issue.lua` | 验证码签发、发送频率与次数限制 |
| 同上 | `verification-consume.lua` | 一次性校验验证码并限制错误尝试 |
| 同上 | `verification-revoke.lua` | 撤销验证码 |

## 本地运行

### 1. 准备环境

- 安装 JDK 17 或兼容的更高版本，并确保 `JAVA_HOME` 和 Maven 使用的 JDK 一致。
- 安装 Maven，确认 `java -version` 与 `mvn -v` 可执行；当前项目未提供 Maven Wrapper。
- 准备 MySQL、Redis 和 RabbitMQ。RabbitMQ 需要支持项目使用的 quorum 队列及相应死信配置，并授予应用账号目标 vhost 的访问权限。
- 为邮件验证码和中奖邮件准备可用的 SMTP 服务；短信需要另行实现供应商适配器。
- **事先准备数据库结构和必要业务数据。** 当前没有自动迁移执行器，仓库也不包含 SQL，启动应用不会自动创建完整业务表。

### 2. 设置环境变量

主配置文件为 `luckyflow-app/src/main/resources/application.yml`。数据库、RabbitMQ、SMTP 凭据及运行密钥均通过环境变量注入。

| 环境变量 | 说明 |
| --- | --- |
| `LUCKYFLOW_DB_URL` | 默认 `jdbc:mysql://localhost:3306/lottery` |
| `LUCKYFLOW_DB_USERNAME` / `LUCKYFLOW_DB_PASSWORD` | MySQL 用户名与密码；按本地数据库填写 |
| `LUCKYFLOW_REDIS_HOST` / `LUCKYFLOW_REDIS_PORT` | 默认 `127.0.0.1` / `6379` |
| `LUCKYFLOW_RABBITMQ_HOST` / `LUCKYFLOW_RABBITMQ_PORT` | 默认 `127.0.0.1` / `5672` |
| `LUCKYFLOW_RABBITMQ_USERNAME` / `LUCKYFLOW_RABBITMQ_PASSWORD` | RabbitMQ 用户名与密码 |
| `LUCKYFLOW_RABBITMQ_VIRTUAL_HOST` | 默认 `/` |
| `LUCKYFLOW_JWT_SECRET` | 必填：至少 32 字节随机值的 Base64 编码 |
| `LUCKYFLOW_VERIFICATION_PEPPER` | 必填：独立的验证码 HMAC 密钥 |
| `LUCKYFLOW_ALLOWED_ORIGINS` | 允许的浏览器来源，默认 `http://localhost:5173` |
| `LUCKYFLOW_SMTP_HOST` / `LUCKYFLOW_SMTP_PORT` | SMTP 地址与端口，按服务商要求填写 |
| `LUCKYFLOW_SMTP_USERNAME` / `LUCKYFLOW_SMTP_PASSWORD` | SMTP 用户名与密码/授权码 |
| `LUCKYFLOW_SMTP_AUTH` / `LUCKYFLOW_SMTP_STARTTLS` | 是否启用 SMTP 认证及 STARTTLS，默认均为 `false` |
| `LUCKYFLOW_NOTIFICATION_EMAIL_FROM` | 邮件发件人，需与 SMTP 服务配置匹配 |
| `LUCKYFLOW_FULFILLMENT_CLAIM_PERIOD_DAYS` | 实物奖品领取窗口，默认 7 天 |

下面是 Windows PowerShell 示例，请替换连接凭据，并在同一窗口启动应用：

```powershell
$env:LUCKYFLOW_DB_URL = 'jdbc:mysql://localhost:3306/lottery'
$env:LUCKYFLOW_DB_USERNAME = '替换为数据库用户名'
$env:LUCKYFLOW_DB_PASSWORD = '替换为数据库密码'
$env:LUCKYFLOW_REDIS_HOST = '127.0.0.1'
$env:LUCKYFLOW_REDIS_PORT = '6379'
$env:LUCKYFLOW_RABBITMQ_HOST = '127.0.0.1'
$env:LUCKYFLOW_RABBITMQ_PORT = '5672'
$env:LUCKYFLOW_RABBITMQ_USERNAME = '替换为消息队列用户名'
$env:LUCKYFLOW_RABBITMQ_PASSWORD = '替换为消息队列密码'
$env:LUCKYFLOW_RABBITMQ_VIRTUAL_HOST = '/'

# 分别生成两个随机密钥，不要复用测试配置中的固定值。
$jwtBytes = New-Object byte[] 32
$pepperBytes = New-Object byte[] 32
$keyGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$keyGenerator.GetBytes($jwtBytes)
$keyGenerator.GetBytes($pepperBytes)
$env:LUCKYFLOW_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$env:LUCKYFLOW_VERIFICATION_PEPPER = [Convert]::ToBase64String($pepperBytes)
$keyGenerator.Dispose()

$env:LUCKYFLOW_ALLOWED_ORIGINS = 'http://localhost:5173'

# 使用邮件功能时填写；端口、AUTH、STARTTLS 按 SMTP 服务要求配置。
$env:LUCKYFLOW_SMTP_HOST = '替换为SMTP服务器地址'
$env:LUCKYFLOW_SMTP_PORT = '587'
$env:LUCKYFLOW_SMTP_USERNAME = '替换为SMTP用户名'
$env:LUCKYFLOW_SMTP_PASSWORD = '替换为SMTP授权码'
$env:LUCKYFLOW_SMTP_AUTH = 'true'
$env:LUCKYFLOW_SMTP_STARTTLS = 'true'
$env:LUCKYFLOW_NOTIFICATION_EMAIL_FROM = '替换为已授权发件邮箱'
```

上述密钥生成示例适合本地开发。部署时应生成并持久配置自己的密钥，避免每次重启更换 JWT 密钥使已有令牌失效。PowerShell 的 `$env:` 仅对当前进程及其子进程生效；从 IDE 启动时，需要在运行配置中填写环境变量。

Spring Boot 不会自动读取普通 `.env` 文件。凭据可放在被忽略的本地配置中或通过运行环境注入，不要直接写入将要提交的公共 `application.yml`。

### 3. 构建与启动

在本地 `LuckyFlow/` 根目录（含聚合 `pom.xml`）执行：

```powershell
# 构建启动模块及其依赖，跳过测试执行。
mvn -pl luckyflow-app -am clean package -DskipTests

# 启动打包后的应用。
java -jar .\luckyflow-app\target\luckyflow-app-1.0.0-SNAPSHOT.jar
```

也可先将各模块安装到本地 Maven 仓库，再使用开发模式启动：

```powershell
mvn install -DskipTests
mvn -pl luckyflow-app spring-boot:run
```

启动类为 `com.luckyflow.LuckyFlowApplication`，未额外配置时 HTTP 端口为 `8080`。可查询公开活动接口确认 HTTP 服务及数据库查询正常：

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/activities?page=1&size=10'
```

## 接口与联调约定

接口参数与返回结构可参考 [OpenAPI 文件](docs/openapi-day24.yaml)。该文件与 [Day24 交付记录](docs/backend-day24-delivery.md) 属于早期交付文档，后续实现以当前源码为准；例如当前已包含活动发布意图和定时恢复，不再适用旧交付记录中“尚未实现发布恢复”的结论。项目未配置 Swagger UI。

| 接口范围 | 用途 | 认证 |
| --- | --- | --- |
| `POST /api/auth/verification-codes` | 发送验证码 | 匿名可用，受验证码限流控制 |
| `POST /api/auth/register` | 注册 | 匿名可用 |
| `POST /api/auth/login/password`、`/api/auth/login/code` | 登录 | 匿名可用 |
| `GET /api/auth/me`、`/api/me/**` | 当前用户、积分、历史及领奖 | Bearer JWT |
| `GET /api/activities`、`/api/activities/{activityId}` | 公开活动查询 | 匿名可用 |
| `GET /api/activities/{activityId}/eligibility` | 抽奖资格查询 | Bearer JWT |
| `POST /api/draws` | 抽奖 | Bearer JWT + `Idempotency-Key` |
| `GET /api/draw-requests/**` | 原请求和未决请求查询 | Bearer JWT |
| `/api/admin/**` | 活动、奖品、履约、统计等管理接口 | ADMIN 角色 |

- 登录后通过 `Authorization: Bearer <token>` 访问受保护接口。
- 统一响应结构为 `{"code":200,"message":"success","data":...}`；分页从 1 开始。
- HTTP DTO 中的 Long 主键以十进制字符串表示，前端应避免转为 JavaScript `Number`。
- 业务时区为 `Asia/Shanghai`；接口中的本地日期时间采用 `yyyy-MM-dd'T'HH:mm:ss`。
- 用户身份由 JWT 提供，客户端不能通过自报 `userId` 访问他人资源。

## 外部服务与运行说明

- 短信供应商适配尚未接入；邮件功能需要真实可用的 SMTP 配置。
