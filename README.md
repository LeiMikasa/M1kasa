# M1kasa · 巷里

本地生活 H5 项目，使用 React 前端和 Spring Boot 微服务后端，正在逐步实现店铺、用户、优惠券与秒杀业务。

## 当前功能

- 手机验证码登录、当前用户查询、登录态续期和退出登录。
- 店铺分类、分页列表、名称搜索、详情和基于位置的查询。
- 上架优惠券查询，以及仅在 `local` 环境提供的秒杀券发布入口。
- WebFlux 网关路由、Redis token 鉴权、跨域支持。
- 前端已接入登录、店铺和优惠券查询，包含加载、失败重试、空结果及登录失效处理。

购买、异步创建订单、故事发布和消息服务尚未完成。故事页面目前保留内容预览，前端不会模拟真实下单成功。

## 项目结构

```text
M1kasa/
├── frontend/                     React + TypeScript + Vite H5
├── M1kasa-backend/
│   ├── m1kasa-common-base/       公共响应结构和基础代码
│   ├── m1kasa-user-api/          用户 DTO
│   ├── m1kasa-user-service/      用户服务
│   ├── m1kasa-shop-api/          店铺 DTO
│   ├── m1kasa-shop-service/      店铺服务
│   ├── m1kasa-voucher-api/       优惠券 DTO
│   ├── m1kasa-voucher-service/   优惠券服务
│   └── m1kasa-gateway/           WebFlux 网关
└── 需求文档.md
```

## 本地配置

后端使用 Java 17+、Maven、MySQL 和 Redis。需要准备 `hmdp` 数据库及对应表；仓库不包含开发机器上的业务数据。

每个运行服务的 `src/main/resources/` 中都有 `application.example.yaml`。首次克隆后，分别将四份示例复制为同目录的 `application.yaml`。

真实 `application.yaml` 已被 Git 忽略。示例通过环境变量读取连接信息，请在 IDEA 对应的运行配置或启动进程中设置：

| 变量 | 用途 |
| --- | --- |
| `DB_URL` | JDBC URL，例如 `jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=UTC` |
| `DB_USERNAME` | MySQL 用户名 |
| `DB_PASSWORD` | MySQL 密码 |
| `REDIS_HOST` | Redis 地址，示例默认 `127.0.0.1` |
| `REDIS_PORT` | Redis 端口，示例默认 `6379` |
| `REDIS_PASSWORD` | Redis 密码 |

网关只需要 Redis 相关变量。四个服务须使用同一套 Redis 连接和数据库编号。

验证码实现仅在 `local` profile 下可用，本地联调时给用户服务增加启动参数 `--spring.profiles.active=local`。需要使用本地发布入口时，优惠券服务也启用此 profile。正式商家发布权限尚待实现，`local` profile 不能代替权限校验。

Spring Boot 不会自动把普通 `.env` 文件加载为系统环境变量；后端请使用 IDEA 的 Environment variables 或实际进程环境变量。

## 启动

使用 IDEA 打开 `M1kasa-backend/pom.xml`，加载 Maven 后启动各服务：

| 服务 | 端口 |
| --- | --- |
| 用户服务 | 8081 |
| 店铺服务 | 8082 |
| 优惠券服务 | 8083 |
| 网关 | 8080 |

前端在 `frontend/` 中执行：

```bash
npm install
npm run dev -- --port 5173
```

访问 `http://localhost:5173/`。前端默认请求 `http://localhost:8080` 网关，可通过 Vite 环境变量 `VITE_API_BASE_URL` 调整。更换前端来源地址时，需要同步修改网关允许的跨域来源。

本地验证码在用户服务控制台输出。金额字段 `payValue`、`actualValue` 使用整数分，前端展示时转换为元；秒杀活动时间约定为北京时间。

## 前端检查

```bash
npm run check:runtime
npm run build
```

前端包含手机预览运行时，相关修改约束见 `frontend/AGENTS.md`。
