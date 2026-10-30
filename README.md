# M1kasa · 巷里

本地生活 H5 项目，基于 React 和 Spring Boot 微服务，覆盖店铺浏览、验证码登录、优惠券秒杀、异步订单、探店笔记和图片上传。

当前已接通真实抢券与订单查询、笔记发布与详情、Redis 点赞接口。功能进度按 2026-10-30 的源码整理；点赞的 MQ 投递与数据库同步仍在开发。

## 当前功能

| 业务 | 已实现内容 |
| --- | --- |
| 用户 | 手机验证码登录、当前用户查询、批量用户查询、登录态续期、退出登录 |
| 店铺 | 分类、分页列表、名称搜索、详情、基于 Redis GEO 的附近店铺查询 |
| 优惠券 | 店铺优惠券列表、本地测试券发布与预热、秒杀资格判断、库存预扣、一人一券 |
| 订单 | RabbitMQ 异步创建订单、我的订单分页、订单详情、按优惠券查询当前用户订单 |
| 消息处理 | Redis / MySQL Outbox、发布确认、失败重试、消费幂等、死信路由；订单创建死信采集入库和本地查询、重发 |
| 内容 | 探店笔记发布、我的笔记分页、笔记详情、通过 OpenFeign 查询作者信息 |
| 图片 | JPEG / PNG 上传、内容与尺寸校验、MinIO 存储、临时签名链接 |
| 点赞 | Redis 点赞与取消、状态查询、按需初始化、版本号、待发送记录；前端已接入 GET / PUT |
| 公共能力 | WebFlux 网关、Redis Token 鉴权、跨域配置、MVC 公共鉴权模块、MinIO 与 Feign 客户端自动装配 |

前端的真实业务入口：

- **附近 → 店铺详情 → 优惠券详情 → 登录后抢券 → 查询真实订单**。
- **发布 → 上传图片 → 发布笔记**；在 **我的 → 我的笔记** 查看详情并操作点赞。

抢券受理成功表示获得资格，订单由后台异步创建。支付、退款、评论、关注和完整消息中心尚未接通真实业务；首页故事等示例内容仍保留预览展示。

**点赞目前只完成 Redis 侧处理和前端接入**：已定义消息结构并写入 Redis 待发送记录，尚未实现点赞 Outbox 投递、RabbitMQ 消费落库和故障恢复。当前不能依靠 MySQL 恢复尚未同步的点赞变更。

## 技术栈

| 层次 | 当前选型 |
| --- | --- |
| 前端 | React 19、TypeScript、Vite 8 |
| 后端 | Java 17、Spring Boot 4.1.1、Spring MVC |
| 网关 | Spring Cloud Gateway Server WebFlux、Reactive Redis |
| 服务调用 | Spring Cloud 2025.1.4、OpenFeign、LoadBalancer、Nacos Discovery |
| 注册发现依赖 | Spring Cloud Alibaba 2025.1.0.0 |
| 数据与缓存 | MySQL、MyBatis-Plus 3.5.17、Redis、Lua |
| 异步消息 | RabbitMQ、Spring AMQP |
| 图片存储 | MinIO、公共存储模块自动装配 |
| 验证工具 | JMeter、Python 监测脚本、Playwright |

## 项目结构

```text
M1kasa/
├── frontend/                       React + TypeScript + Vite H5
├── M1kasa-backend/
│   ├── pom.xml                     Maven 父工程与模块聚合
│   ├── m1kasa-common-base/          公共响应、用户上下文等基础代码
│   ├── m1kasa-common-auth/          MVC 登录拦截器与自动装配
│   ├── m1kasa-common-storage/       MinIO 客户端、模板与自动装配
│   ├── m1kasa-user-api/             用户 DTO、Feign 客户端与自动装配
│   ├── m1kasa-user-service/         用户服务
│   ├── m1kasa-shop-api/             店铺接口数据结构
│   ├── m1kasa-shop-service/         店铺服务
│   ├── m1kasa-voucher-api/          优惠券接口与下单消息
│   ├── m1kasa-voucher-service/      优惠券、秒杀资格、库存同步
│   ├── m1kasa-order-api/            订单接口与事件数据结构
│   ├── m1kasa-order-service/        异步订单、Outbox、订单死信处理
│   ├── m1kasa-content-api/          笔记、图片、点赞接口数据结构
│   ├── m1kasa-content-service/      笔记、图片上传、Redis 点赞
│   └── m1kasa-gateway/              WebFlux 网关
├── load-test/                      秒杀压测、监测与结果核验工具
├── docs/                           联调、方案、压测与实现说明
└── 需求文档.md
```

`*-api` 和 `common-*` 是被业务服务依赖的库模块，无需单独启动。

## 主要链路

### 秒杀与订单

```text
前端 → 网关 → 优惠券服务
    → Redis Lua：资格校验 + 预扣库存 + 防重复领取 + 写 Redis Outbox
    → 返回获得资格

优惠券 Outbox 任务 → RabbitMQ 下单消息
    → 订单服务幂等消费
    → 本地事务：保存订单 + MySQL Outbox
    → 订单 Outbox 任务 → RabbitMQ 订单已创建事件
    → 优惠券服务幂等消费
    → 本地事务：保存扣减流水 + 扣减 MySQL 库存
```

发送失败时保留 Outbox 记录，后续任务继续投递。发布确认后清理待发送记录或更新发送状态；重复投递由订单、流水的唯一约束及消费逻辑处理。订单创建死信还可采集入库并通过本地接口重发，版本条件用于避免旧重发结果覆盖新采集状态。

该链路采用异步最终一致性。发布确认与消费成功是不同阶段，最终失败后的业务补偿和 Redis 数据丢失恢复仍需完善。详细设计见 [秒杀下单与库存一致性方案](docs/秒杀下单与库存一致性方案.md)。

### 笔记与点赞

内容服务通过 OpenFeign 调用用户服务，LoadBalancer 结合 Nacos 中的实例信息选择目标服务；当前调用显式传递 `Authorization` 请求头。

点赞提交明确的目标状态 `liked=true/false`，Lua 在状态发生变化时一起更新用户状态、总数、版本号和待发送记录。同状态重复提交不会重复加减计数。缺少初始数据时，先读取数据库，再通过初始化 Lua 按条件填充 Redis，保留已存在的数据。

点赞状态查询使用普通 `HGET` 和 `GET`，允许展示时短暂不一致。后续 MQ 落库设计及初始化并发说明见 [点赞功能实现与问答总结](docs/点赞功能实现与问答总结-20261030.md)。

## 本地运行

### 1. 准备环境

- Java 17+、Maven、Node.js 与 npm。
- MySQL、Redis、RabbitMQ；图片上传需要 MinIO，内容服务调用用户服务需要 Nacos。
- 按实体及相关文档准备数据库表，包括订单 Outbox、库存扣减流水、订单死信和点赞关系表。仓库目前没有完整的一键建库脚本或演示数据包，Maven 构建不会自动建表。
- MinIO 预先创建存储桶；配置的 API 地址需要内容服务和前端浏览器均可访问，以便使用返回的签名图片链接。

### 2. 配置各服务

本地 `application.yaml` / `application.yml` 已由 Git 忽略。用户、店铺、优惠券、订单和网关目录提供了 `application.example.yaml`，首次运行时复制到同目录的 `application.yaml`；已有本地配置无需覆盖。

**示例配置还未覆盖全部新增功能**：优惠券与订单的 MQ、Outbox，用户的 Nacos，以及网关的内容路由需要补齐。内容服务目前没有示例配置，需要在其 `src/main/resources/` 下新建本地配置，设置 `server.port=8085`、`spring.application.name=m1kasa-content-service`，并配置数据源、Redis、Nacos、MinIO 和公共鉴权。

| 配置位置 | 需要配置的内容 |
| --- | --- |
| `spring.datasource` | 数据库 URL、用户名、密码；订单示例使用独立的 `ORDER_DB_URL` |
| `spring.data.redis` | 地址、端口、密码、数据库编号；网关和业务服务应共享登录态所在的 Redis 数据库 |
| `spring.rabbitmq` | 优惠券与订单服务使用相同 RabbitMQ 实例和 virtual host |
| `spring.cloud.nacos.discovery` | 用户、内容服务的注册中心地址、namespace、group 及鉴权信息（若启用） |
| `m1kasa.minio` | 内容服务的 API 地址、访问密钥、存储桶及启用开关 |
| `m1kasa.auth` | MVC 服务鉴权范围；内容服务保护 `/blog/**` |

现有示例使用 `DB_URL`、`ORDER_DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 等环境变量。请在 IDEA 的运行配置或启动进程环境中设置；Spring Boot 不会自动将普通 `.env` 文件作为系统环境变量加载。

下面是需要合并到对应配置位置的片段。已有同名 YAML 节点时，合并其内容，避免重复声明顶层 `spring` 或 `m1kasa`。

优惠券与订单服务均需配置 MQ：

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:127.0.0.1}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: ${RABBITMQ_VHOST:/}
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
    listener:
      type: simple
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        prefetch: 10
        retry:
          enabled: true
          max-retries: 2
          initial-interval: 1s
          multiplier: 2
          max-interval: 5s
```

同时分别打开后台任务：

| 服务 | 配置项 | 用途 |
| --- | --- | --- |
| 优惠券 | `m1kasa.seckill.outbox.enabled=true` | 投递 Redis 中的下单待发送记录 |
| 订单 | `m1kasa.order.outbox.enabled=true` | 投递订单已创建事件 |
| 订单 | `m1kasa.order.dead-letter.enabled=true` | 启用订单创建死信的采集入库 |

用户与内容服务的 Nacos 配置需要使用相同的 namespace 和 group。以下环境变量需按片段自行设置：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:}
        group: ${NACOS_GROUP:DEFAULT_GROUP}
```

内容服务还需配置存储与鉴权。下面的 `MINIO_*` 变量由该片段定义；启用 MinIO 自动装配后，才会提供上传服务依赖的存储组件：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB

m1kasa:
  auth:
    enabled: true
    include-paths:
      - /blog/**
  minio:
    enabled: true
    endpoint: ${MINIO_ENDPOINT:http://127.0.0.1:9000}
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
    bucket: ${MINIO_BUCKET:m1kasa}
```

网关目前使用固定 HTTP 地址路由，尚未切换为 Nacos 的 `lb://` 路由。在 `spring.cloud.gateway.server.webflux.routes` 列表中补上内容服务：

```yaml
- id: content-service
  uri: http://localhost:8085
  predicates:
    - Path=/blog/**
```

网关跨域配置需要允许前端来源、`Authorization` / `Content-Type` 请求头，以及点赞所用的 `PUT` 方法。

### 3. 构建并启动后端

使用 IDEA 打开 `M1kasa-backend/pom.xml`，按 Maven 工程加载。首次构建从聚合工程目录执行，将本地 API 和公共模块安装到 Maven 仓库：

```powershell
cd .\M1kasa-backend
mvn -Dmaven.test.skip=true install
```

该命令跳过测试编译与执行，用于先准备本地模块依赖。修改单个服务后可从父工程使用 `-pl :模块名 -am` 一起构建所需模块，避免本地 SNAPSHOT 依赖缺失。

先启动基础设施，再启动各业务服务，最后启动网关：

| 服务 | 端口 | 主要依赖 |
| --- | --- | --- |
| 用户服务 | 8081 | MySQL、Redis、Nacos |
| 店铺服务 | 8082 | MySQL、Redis |
| 优惠券服务 | 8083 | MySQL、Redis、RabbitMQ |
| 订单服务 | 8084 | MySQL、Redis、RabbitMQ |
| 内容服务 | 8085 | MySQL、Redis、MinIO、Nacos、用户服务 |
| 网关 | 8080 | Redis、对应业务服务 |

本地验证码接口仅在用户服务启用 `local` profile 时提供，验证码输出到服务控制台。需要创建和预热测试秒杀券时，优惠券服务也启用 `local`；使用订单死信查询、重发接口时，订单服务启用 `local`，并从本机直接访问 `http://localhost:8084/local/mq/dead-letters`。

可在 IDEA 设置 Active profiles 为 `local`，或使用启动参数 `--spring.profiles.active=local`。这些本地测试入口尚未提供正式运营权限体系。

### 4. 启动前端

从仓库根目录执行：

```powershell
cd .\frontend
npm install
npm run dev -- --port 5173
```

访问 `http://localhost:5173/`。前端默认请求 `http://localhost:8080` 网关，通过 Vite 环境变量 `VITE_API_BASE_URL` 可修改 API 地址；修改后重启前端开发服务，并同步网关允许的跨域来源。

金额字段 `payValue`、`actualValue` 使用整数分，前端转换为元；秒杀活动时间约定为北京时间。图片当前仅接受不超过 5MB 的 JPEG / PNG，上传预览链接有效期为 10 分钟。

## 检查与压测

前端检查在 `frontend/` 中执行：

```powershell
npm run check:runtime
npm run build
```

前端包含手机预览运行时，修改约束见 [frontend/AGENTS.md](frontend/AGENTS.md)。真实业务联调需要运行对应后端及基础设施，构建成功不代表业务链路已经联通。

2026-10-28 的单机微服务压测，经网关访问秒杀接口，压测端与应用端同机，突发场景预先建立连接：

| 场景 | 实际结果 |
| --- | --- |
| 500 个工作线程同时抢 10 张券 | 获得资格 10、售罄 490、技术异常 0；HTTP P95 382ms |
| 500 个工作线程同时抢 500 张券 | 获得资格 500、最终订单与扣减流水均为 500；技术异常 0；HTTP P95 525ms |
| 目标 10 请求/秒，持续 60 秒 | 600 次有效受理，HTTP 平均 8.95ms、P95 15ms；最终订单与流水均为 600 |
| 目标 40 请求/秒，持续 60 秒 | 2400 次有效受理，HTTP P95 13ms；后台积压增长，请求结束后约 9.489 秒首次观测排空 |

这些是已验证负载；突发 RPS、HTTP 受理速率和后台完整处理能力需要分别衡量，尚未测出系统最大并发上限。详细环境、统计口径与积压趋势见 [压测报告](docs/压测报告-20261028.md)，复现步骤见 [load-test/README.md](load-test/README.md)。以上数据不代表点赞链路性能。

## 相关文档

- [需求文档](需求文档.md)
- [前后端联调说明](docs/前后端联调说明.md)
- [秒杀下单与库存一致性方案](docs/秒杀下单与库存一致性方案.md)
- [Redis Outbox 实现说明](docs/秒杀下单-Redis-Outbox实现说明.md)
- [点赞功能实现与问答总结](docs/点赞功能实现与问答总结-20261030.md)
- [秒杀微服务压测报告](docs/压测报告-20261028.md)
- [压测操作步骤](load-test/README.md)
