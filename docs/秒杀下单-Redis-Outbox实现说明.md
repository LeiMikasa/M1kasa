# 秒杀下单：Redis Outbox 待发送记录实现

本文整理当前抢券流程中 Redis Outbox 的实现步骤，代码由开发者手动添加。当前阶段完成待发送记录的保存；消息投递任务、消费幂等和订单创建将在后续实现。

## 1. 为什么需要待发送记录

如果直接按顺序执行 Redis 预扣库存和 RabbitMQ 消息发送，存在下面的失败窗口：

```text
Redis 扣库存成功
    ↓
优惠券服务宕机
    ↓
下单消息没有发送，订单没有创建
```

消息发送组件的发布确认只能帮助判断一次发送的结果，无法恢复尚未执行的发送操作。

本方案在同一个 Lua 脚本中完成库存预扣、用户资格记录和待发送记录保存。后续投递组件读取待发送记录，发送到 RabbitMQ，并在发布检查通过后删除对应记录。

```text
抢券请求
    ↓
Lua 校验时间、重复领取、库存
    ↓
扣库存 + 记录用户 + 保存 Outbox 记录
    ↓
投递组件读取记录
    ↓
RabbitMQ
    ↓
订单服务创建订单
```

## 2. Redis 数据结构

每个优惠券活动使用三个 Key：

| Key 示例 | 类型 | 内容 |
| --- | --- | --- |
| `m1kasa:seckill:activity:{10}` | Hash | 库存、开始时间和结束时间 |
| `m1kasa:seckill:buyers:{10}` | Set | 已获得抢券资格的用户 ID |
| `m1kasa:seckill:outbox:{10}` | Hash | 请求 ID 与用户 ID 的对应关系 |

相同的 `{10}` 是 Redis Cluster 的 Hash Tag，使三个 Key 可以落在同一个槽位，供同一个 Lua 脚本操作。

Outbox 示例：

```text
Key：m1kasa:seckill:outbox:{10}

Hash 字段（requestId）       Hash 值（userId）
request-001                 2474
request-002                 2475
```

`request-001 → 2474` 表示：需要为用户 2474 创建优惠券 10 的订单。优惠券 ID 包含在 Key 中，因此可以还原完整的 `SeckillOrderCreateMessage(requestId, voucherId, userId)`。

## 3. 增加 Redis Key 方法

在优惠券服务的 `constant.SeckillRedisKeys` 中增加：

```java
public static String outbox(Long voucherId) {
    return "m1kasa:seckill:outbox:{" + voucherId + "}";
}
```

已有的 `activity()` 和 `buyers()` 方法保留。

## 4. 修改抢券 Lua 脚本

文件位置：`m1kasa-voucher-service/src/main/resources/lua/seckill_reserve.lua`。

增加 `KEYS[3]` 和 `ARGV[2]`，完整示例如下：

```lua
-- KEYS[1]：活动信息 Hash
-- KEYS[2]：已获得资格的用户 Set
-- KEYS[3]：待发送记录 Hash
-- ARGV[1]：当前用户 ID
-- ARGV[2]：本次抢券请求 ID

-- 返回值：
-- 0：获得资格，并保存待发送记录
-- 1：库存不足
-- 2：重复领取
-- 3：活动尚未开始
-- 4：活动已经结束
-- 5：活动未初始化或数据不完整

local activityKey = KEYS[1]
local buyersKey = KEYS[2]
local outboxKey = KEYS[3]
local userId = ARGV[1]
local requestId = ARGV[2]

if not requestId or requestId == '' then
    return 5
end

-- 在修改数据前检查 Outbox 类型，避免后续 HSET 出现 WRONGTYPE
local outboxType = redis.call('TYPE', outboxKey).ok
if outboxType ~= 'none' and outboxType ~= 'hash' then
    return 5
end

-- 1. 读取活动信息
local activity = redis.call(
    'HMGET', activityKey,
    'stock', 'beginTime', 'endTime'
)

local stock = tonumber(activity[1] or '')
local beginTime = tonumber(activity[2] or '')
local endTime = tonumber(activity[3] or '')

if not stock or not beginTime or not endTime then
    return 5
end

-- 2. 使用 Redis 时间，转换为毫秒
local redisTime = redis.call('TIME')
local now = tonumber(redisTime[1]) * 1000
    + math.floor(tonumber(redisTime[2]) / 1000)

if now < beginTime then
    return 3
end

if now >= endTime then
    return 4
end

-- 3. 校验是否重复领取
if redis.call('SISMEMBER', buyersKey, userId) == 1 then
    return 2
end

-- 4. 校验库存
if stock <= 0 then
    return 1
end

-- 5. 扣库存、记录用户、保存待发送记录
redis.call('HINCRBY', activityKey, 'stock', -1)
redis.call('SADD', buyersKey, userId)
redis.call('HSET', outboxKey, requestId, userId)

return 0
```

脚本正常返回 `0` 时，这三项写入都已完成。其他请求无法在脚本执行过程中插入操作。

Redis Lua 的原子执行不等于出错自动回滚。必须在写入前完成校验，避免错误的数据类型和参数导致脚本中途失败；不能把运行时错误简单当成“完全没有执行”。

## 5. 修改 Java 调用参数

在 `VoucherServiceImpl.reserveSeckill()` 中，保留原来的参数校验和返回值转换，替换脚本调用部分：

```java
// 一次抢券请求生成一个业务请求 ID
String requestId = UUID.randomUUID().toString();

Long result = stringRedisTemplate.execute(
        RESERVE_SCRIPT,
        List.of(
                SeckillRedisKeys.activity(voucherId),
                SeckillRedisKeys.buyers(voucherId),
                SeckillRedisKeys.outbox(voucherId)
        ),
        userId.toString(),
        requestId
);
```

需要导入：

```java
import java.util.UUID;
```

参数对应关系：

| Java 参数 | Lua 参数 |
| --- | --- |
| `SeckillRedisKeys.activity(voucherId)` | `KEYS[1]` |
| `SeckillRedisKeys.buyers(voucherId)` | `KEYS[2]` |
| `SeckillRedisKeys.outbox(voucherId)` | `KEYS[3]` |
| `userId.toString()` | `ARGV[1]` |
| `requestId` | `ARGV[2]` |

Lua 和 Java 参数需要配套修改后再测试。

## 6. 业务请求 ID 与发布确认 ID

| 标识 | 作用 | 重试发送时 |
| --- | --- | --- |
| 消息中的 `requestId` | 标识同一笔抢券业务 | 从 Outbox 读取，保持不变 |
| `CorrelationData` 的 ID | 标识某一次发送尝试 | 每次发送重新生成 |

例如：业务请求 `request-001` 第一次发送使用确认 ID A，重试时使用确认 ID B，但业务请求 ID 仍然是 `request-001`。

RabbitMQ 不会根据相同的 `messageId` 自动去重。订单服务仍然需要使用数据库唯一约束和幂等处理，避免重复创建订单。

## 7. 后续投递组件的处理规则

以下是下一步要实现的流程，并非本步骤已经完成的功能：

```text
发现并读取待发送记录
    ↓
还原 SeckillOrderCreateMessage
    ↓
调用 SeckillOrderPublisher.send()
    ↓
发布检查通过：删除该 requestId 对应的 Hash 字段
失败或超时：保留记录，后续重试
```

实现时需要遵守：

- 发送前保留 Outbox 记录，不能先删除再发送。
- 使用原来的 `requestId` 重试，不要重新生成业务请求 ID。
- 检查发布确认和消息退回，不能仅凭 `convertAndSend()` 返回判断成功。
- 确认超时表示结果未知，消息可能已到达 RabbitMQ，不能据此直接返还库存。
- 发布检查通过后，只删除对应的 Hash 字段，不能删除整个活动的 Outbox Key。
- 投递任务要能在进程重启后重新发现积压记录，也要处理活动结束后仍未发出的记录。
- 交换机和队列由订单消费者通过注解声明时，首次投递前需完成资源声明。

## 8. 方案边界

### 优惠券服务在记录保存后宕机

只要 Redis 中的记录得到保留，服务恢复后就可以读取记录并继续发送，补上“预扣成功、尚未发送”的失败窗口。

### 消息发布成功，删除记录前宕机

恢复后可能再次发送同一条业务消息。因此重复投递是需要处理的正常情况，订单消费者必须实现幂等。

### Redis 本身发生故障

待发送记录的保留能力依赖 Redis 持久化、复制和故障恢复配置。本方案不能脱离这些条件宣称消息绝对不丢。

### 发布确认与订单创建

发布检查通过只代表消息发布获得确认且未被退回，不代表订单数据库事务已经提交。订单创建结果需要由后续订单服务维护和查询。
