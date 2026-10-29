# M1kasa 秒杀微服务压测

本目录测量当前微服务的 `POST /voucher/seckill/{voucherId}/reserve`，经网关 8080，使用真实登录产生的 Bearer token。与旧单体 `/voucher-order/seckill/{id}` 脚本不同。

## 前置条件

- 启动 MySQL、Redis、RabbitMQ，以及网关、用户、店铺、优惠券、订单服务。
- 用户服务与优惠券服务启用 `local` profile，以使用本地验证码和创建/预热活动接口。
- 优惠券服务 `m1kasa.seckill.outbox.enabled=true`，订单服务 `m1kasa.order.outbox.enabled=true`。
- Python 标准库、Java 17+、JMeter 5.6.3；JMeter 命令在 PATH 中。默认通过 `D:/dev-cache/maven-repository` 中的 MySQL JDBC 驱动进行只读监测，可用 `MAVEN_REPO` 覆盖。
- 本机同时承担压测端和应用端。记录 CPU、内存条件，资源不足时先小规模验证；压测结果受同机竞争影响。

准备工具在内存中读取本地 `application.yaml`，环境占位符优先使用当前进程环境，其次使用本机 IDEA 对应运行配置。不会打印或将连接密码写入测试报告。移到其他机器时可通过环境变量提供配置，并用 `M1KASA_PROJECT_ROOT` 指定项目目录。

## 数据范围

每轮新建独立测试券，在活动开始前初始化 Redis。不会重置或删除已有券库存。测试用户使用事先在数据库中查验未占用的随机测试手机号，经本地验证码和真实登录 API 注册。

`.runs/` 中包含登录 token、测试用户与原始运行数据，已由本目录 `.gitignore` 排除。不要提交该目录。测试券、用户、订单和流水保留供复查。

## 1. 准备冒烟活动

```powershell
cd D:\M1kasa
python .\load-test\prepare.py --users 5 --stock 5
```

工具会等待活动实际开始，再打印 `manifest.json` 绝对路径。每个 fixture 只允许做一次测量。

## 2. 先启动只读监测

另开 PowerShell，用同一个 manifest：

```powershell
python .\load-test\monitor.py --manifest '<manifest.json绝对路径>'
```

监测脚本观察 Redis 预扣及待发送记录、订单数、数据库 Outbox、扣减流水和剩余库存。JMeter 完成后仍会等待最终排空，两次采样一致才结束，默认最多 420 秒。

## 3. 执行突发请求

```powershell
.\load-test\run.ps1 -Manifest '<manifest.json绝对路径>' -Mode burst -Threads 5 -RampSeconds 5
```

默认每线程先预建连接，再同时发出一次抢券。预热请求单独统计，排除在抢券指标之外。`-ColdConnections` 测试冷连接，结果须分开标注。默认非 GUI，JMeter 堆上限 256MB，不在测量时生成 HTML。

50/100/200 并发各自使用新 fixture。库存 10 的测试主要观察快速售罄分支，不能代表所有请求都创建订单。另准备库存充足的独立用户突发，才能观察完整有效业务。

## 4. 复用测试用户池

```powershell
python .\load-test\prepare.py --pool-only --users 800
python .\load-test\prepare.py --pool '<pool目录>' --users 100 --stock 10
```

同一用户可参与不同的新券测试，但同一券不能重复领取。用户池 token 有效期 30 分钟；工具会拒绝即将过期的池。

## 5. 持续有效下单

准备至少 `TargetRps × DurationSeconds` 加余量的独立用户，例如 800 个用户、库存 800：

```powershell
python .\load-test\prepare.py --pool '<pool目录>' --users 800 --stock 800
```

启动监测后执行：

```powershell
.\load-test\run.ps1 -Manifest '<manifest.json绝对路径>' -Mode sustained -Threads 20 -RampSeconds 5 -DurationSeconds 60 -TargetRps 10
```

该档目标为每秒 10 次 POST、持续约 60 秒。每次请求消耗一个新 token，避免重复领取形成虚假的高吞吐。线程数是客户端工作线程上限，不是每秒请求数，也不等于全程实际同时在途的请求数。实际到达率和时长以 JTL/summary 为准。

## 6. 合并 HTTP 与业务结果

待监测结束后：

```powershell
python .\load-test\analyze.py --manifest '<manifest.json绝对路径>'
```

HTTP 技术异常、售罄、重复领取与成功资格分别统计。突发 RPS 只使用该波抢券时间窗，不称为稳定 QPS。持续测试若提前耗尽 token 或实际时长不达标，不作为有效持续吞吐结论。

最终应满足：订单数＝已获资格用户数＝库存扣减流水数；订单 Outbox 均已发送；Redis Outbox 排空；Redis 与 MySQL 剩余库存均为初始库存减去资格数。无 HTTP 异常时，再要求 HTTP accepted 与 Redis 资格数一致。

全链路窗口使用首次 HTTP 抢券开始至监测观察到最终库存同步完成。监测默认每 2 秒采样，因此是采样观测值，不提供虚构的毫秒级订单完成 P95/P99。

产物包括 JTL、JMeter 日志、HTTP summary、`monitor.jsonl`、`pipeline-summary.json` 和 `analysis.json`。通过某一档只表示已验证该负载，不代表已找到系统最大承载能力。
