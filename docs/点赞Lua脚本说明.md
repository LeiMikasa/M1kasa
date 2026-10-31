# 点赞 Lua 脚本说明

整理日期：2026-10-30。本文对应当前内容服务中的 `blog_like_init.lua` 和 `blog_like_set.lua`。

两个完整代码块直接取自当前源码，保留原始逻辑与注释。本文整理调用方式与行为，不修改 Lua 文件。

## 1. 先理解两个脚本的分工

| 脚本 | 负责什么 | 不负责什么 |
| --- | --- | --- |
| `blog_like_init.lua` | 把 Java 从数据库查询到的初始值，按条件放入 Redis；保留已经存在的用户状态和共享计数 | 不查询 MySQL，不执行真正的点赞，不写入待发送记录 |
| `blog_like_set.lua` | 根据目标状态执行点赞或取消；状态变化时一起修改状态、计数、版本和待发送记录 | 不查询 MySQL，不直接发送 RabbitMQ，不直接落库 |

可以先记成：**init 准备数据，set 修改数据。**

通常不需要先手动预热。当前 `setLiked()` 会先执行 set；收到 `-2` 时调用初始化，再重试一次 set。`getLikeState()` 读取缺数据时也会使用这个初始化方法。

## 2. 两个脚本共同使用的 Redis 数据

以笔记 `blogId=10` 为例：

| 参数 | Redis Key | 类型 | 用途 |
| --- | --- | --- | --- |
| `KEYS[1]` | `m1kasa:blog:like:state:{10}` | Hash | 每个已加载用户的状态、版本与时间 |
| `KEYS[2]` | `m1kasa:blog:like:count:{10}` | String | 这篇笔记的共享点赞总数 |
| `KEYS[3]` | `m1kasa:blog:like:outbox:{10}` | Hash | 每个用户尚待发送的最新变更 |

这里是 **3 个 Key、2 种数据结构**：两个 Hash 和一个 String。

状态与 Outbox 的 Hash 字段都是用户 ID，值的格式是：

```text
点赞状态:版本号:操作时间

1:3:1793361600000
│ │ └─ 操作时间，毫秒时间戳
│ └─── 当前用户对这篇笔记的状态版本
└───── 1 表示点赞，0 表示未点赞
```

示例：

```text
state:{10}  Hash
  1001 → "1:3:1793361600000"
  1002 → "0:0:0"

count:{10}  String
  "21"

outbox:{10}  Hash
  1001 → "1:3:1793361600000"
```

- `1002 → "0:0:0"` 说明这个用户已经加载过，当前未点赞。它不是“没有初始化”。
- `count` 是整篇笔记的计数，不是当前用户自己的计数；也不能用状态 Hash 的字段数量代替。
- 版本号按“用户 + 笔记”独立递增。新关系从 0 开始；数据库已有关系时继承其版本。
- Outbox 按用户覆盖为最新状态，同一用户连续操作可能合并成一份待发送记录。
- 三个 Key 中相同的 `{10}` 是 Hash Tag，为 Redis Cluster 下的同槽操作提供条件，不表示当前已经部署 Cluster。

## 3. 初始化脚本：blog_like_init.lua

### 3.1 输入参数

`KEYS` 使用上一节约定。`ARGV` 是 Java 传入的业务参数，均按字符串传递：

| 参数 | 含义 | 例子 |
| --- | --- | --- |
| `ARGV[1]` | 当前用户 ID | `"1001"` |
| `ARGV[2]` | 数据库中的笔记点赞总数 | `"20"` |
| `ARGV[3]` | 数据库中的该用户点赞状态 | `"0"` 或 `"1"` |
| `ARGV[4]` | 数据库中的该用户状态版本 | `"0"` |
| `ARGV[5]` | 数据库更新时间转换成的毫秒时间戳 | `"0"` 或实际时间戳 |

用户从来没有关系记录时，Java 使用未点赞、版本 0、时间 0。数据库已存在的取消点赞记录也需要读取，以保留已有版本。

数据库查询发生在 Java 的 `BlogLikeCacheInitializer.initialize()` 中，Lua 只接收查询结果。

### 3.2 完整代码

```lua
-- KEYS[1]: 点赞状态Hash
-- KEYS[2]: 点赞总数String
-- KEYS[3]: 待发送记录Hash

--ARGV[1]:用户ID
--ARGV[2]:数据库中的点赞总数
--ARGV[3]:数据库中的用户状态，0或1
--ARGV[4]:数据库中的用户状态版本
--ARGV[5]:数据库中的更新时间，毫秒时间戳

local stateKey = KEYS[1]
local countKey = KEYS[2]
local outboxKey = KEYS[3]

local userId = ARGV[1]
local initialCount = ARGV[2]
local initialLiked = ARGV[3]
local initialVersion = ARGV[4]
local initialTime = ARGV[5]

-- 判断是否为非负整数字符串
local function isUnsignedInteger(value)
    return value and string.match(value, '^%d+$') ~= nil
end

-- 1. 校验参数，参数不合法时不修改 Redis
if not isUnsignedInteger(userId) or tonumber(userId) <= 0 then
    return -1
end

if not isUnsignedInteger(initialCount)
    or not isUnsignedInteger(initialVersion)
    or not isUnsignedInteger(initialTime) then
    return -1
end

if initialLiked ~= '0' and initialLiked ~= '1' then
    return -1
end

-- 2. 检查 Key 类型，避免写到一半遇到 WRONGTYPE
local stateType = redis.call('TYPE', stateKey).ok
local countType = redis.call('TYPE', countKey).ok
local outboxType = redis.call('TYPE', outboxKey).ok

if stateType ~= 'none' and stateType ~= 'hash' then
    return -3
end

if countType ~= 'none' and countType ~= 'string' then
    return -3
end

if outboxType ~= 'none' and outboxType ~= 'hash' then
    return -3
end
local stateExists = stateType ~= 'none'
local countExists = countType ~= 'none'

-- 3. 状态和计数只剩一部分，不能直接使用旧数据库值覆盖
if stateExists ~= countExists then
    return -2
end

-- 有待发送数据，但状态和计数丢失，需要单独恢复
if not countExists and outboxType ~= 'none' then
    return -2
end

-- 4. 当前用户已经初始化，保留 Redis 中的最新状态
if countExists
        and redis.call('HEXISTS', stateKey, userId) == 1 then
    return 0
end

-- 5. 只有首次初始化这篇笔记时，才装入数据库计数
if not countExists then
    redis.call('SET', countKey, initialCount)
end

-- 6. 只初始化当前用户，不覆盖已有用户状态
local initialState =
        initialLiked .. ':' .. initialVersion .. ':' .. initialTime

redis.call('HSETNX', stateKey, userId, initialState)

return 1
```

### 3.3 按执行顺序理解

1. **取出参数。** `KEYS` 是操作哪些 Redis Key，`ARGV` 是写入时使用的初始值。
2. **检查参数格式。** 用户 ID 必须大于 0；计数、版本、时间必须是非负整数字符串；点赞状态只能是 `0` 或 `1`。
3. **检查 Redis 类型。** `TYPE` 返回状态对象，代码用 `.ok` 取出 `hash`、`string`、`none` 等类型名称。`none` 表示 Key 不存在。
4. **拦住明显的部分数据丢失。** 状态 Hash 与计数 String 只存在其中一个，或者两者都没有但 Outbox 还在，返回 `-2`，不使用数据库旧值直接补齐。
5. **当前用户已加载则直接返回。** `HEXISTS` 判断这个 Hash 里是否已经有当前用户字段，有则返回 `0`，不重置状态或计数。
6. **计数不存在才写入。** 只有通过前面检查、首次加载这篇笔记时，才执行 `SET countKey initialCount`。计数存在时，无论是否被别人改过，都保留当前值。
7. **补当前用户状态。** 拼接 `状态:版本:时间`，通过 `HSETNX` 只在字段不存在时写入，然后返回 `1`。

这个脚本检查 Outbox，但不写 Outbox。加载一个未操作过的用户，不代表产生了一次点赞变更。

### 3.4 返回值

初始化返回的是一个数字，Java 使用 `DefaultRedisScript<Long>` 接收。

| 返回值 | 含义 | Java 当前处理 |
| --- | --- | --- |
| `1` | 完成当前用户的初始化 | 正常继续 |
| `0` | 当前用户已经加载，保留 Redis 数据 | 正常继续 |
| `-1` | 输入参数不合法 | 停止，抛出初始化失败异常 |
| `-2` | 检测到部分缓存数据不完整 | 停止，提示需要恢复 |
| `-3` | Redis Key 类型错误 | 停止，提示类型错误 |

`0` 是成功分支，不是初始化失败。

### 3.5 Java 如何传入参数

下面是当前初始化调用的参数顺序：

```java
Long result = stringRedisTemplate.execute(
        INIT_SCRIPT,
        List.of(
                BlogLikeRedisKeys.state(blogId),
                BlogLikeRedisKeys.count(blogId),
                BlogLikeRedisKeys.outbox(blogId)
        ),
        userId.toString(),
        Integer.toString(initialCount),
        initialLiked ? "1" : "0",
        Long.toString(initialVersion),
        Long.toString(initialTime)
);
```

`List.of(...)` 的三个值依次对应 `KEYS[1..3]`，后面的五个字符串依次对应 `ARGV[1..5]`。Lua 数组下标从 1 开始。

## 4. 点赞脚本：blog_like_set.lua

### 4.1 输入参数

`KEYS` 仍然是状态、计数、Outbox 三个 Key。这次的 `ARGV` 与初始化脚本不同：

| 参数 | 含义 | 例子 |
| --- | --- | --- |
| `ARGV[1]` | 当前用户 ID | `"1001"` |
| `ARGV[2]` | 这次希望设置成的状态 | 点赞 `"1"`，取消 `"0"` |
| `ARGV[3]` | 本次操作的毫秒时间戳 | `"1793361600000"` |

请求表达的是“我要变成已点赞 / 未点赞”，不是“把当前状态翻转一次”。这样相同目标的重复请求不会重复加减计数。

### 4.2 完整代码

```lua
-- KEYS[1]：点赞状态 Hash
-- KEYS[2]：点赞总数 String
-- KEYS[3]：待发送记录 Hash

-- ARGV[1]：用户 ID
-- ARGV[2]：目标状态，1 点赞，0 取消点赞
-- ARGV[3]：本次操作时间，毫秒时间戳

local stateKey = KEYS[1]
local countKey = KEYS[2]
local outboxKey = KEYS[3]

local userId = ARGV[1]
local targetLiked = ARGV[2]
local occurredAt = ARGV[3]


-- 当前数据库和 DTO 中点赞总数使用 int
local MAX_COUNT = 2147483647

-- 控制在 Lua 能精确表示的整数范围内
local MAX_VERSION = 9007199254740991
local function isUnsignedInteger(value)
    return value and string.match(value, '^%d+$') ~= nil
end


-- 1. 校验参数
if not isUnsignedInteger(userId) or tonumber(userId) <= 0 then
    return { -1 }
end


if targetLiked ~= '0' and targetLiked ~= '1' then
    return { -1 }
end

if not isUnsignedInteger(occurredAt)
        or tonumber(occurredAt) <= 0
        or tonumber(occurredAt) > MAX_VERSION then
    return {-1}
end

-- 2. 校验 Redis Key 类型
local stateType = redis.call('TYPE', stateKey).ok
local countType = redis.call('TYPE', countKey).ok
local outboxType = redis.call('TYPE', outboxKey).ok

if stateType ~= 'none' and stateType ~= 'hash' then
    return {-3}
end

if countType ~= 'none' and countType ~= 'string' then
    return {-3}
end

if outboxType ~= 'none' and outboxType ~= 'hash' then
    return { -3 }
end

-- 3. 缓存尚未准备好，交给 Java 调用初始化逻辑
if stateType == 'none' or countType == 'none' then
    return {-2}
end

local currentState = redis.call('HGET', stateKey, userId)
local pendingState = redis.call('HGET', outboxKey, userId)

if not currentState then
    -- 待发送记录还在，但用户状态丢失，不能按普通初始化处理
    if pendingState then
        return { -4 }
    end

    return { -2 }
end

-- 待发送记录存在时，应当与当前用户状态一致
if pendingState and pendingState ~= currentState then
    return {-4}
end
-- 4. 解析当前状态，例如：0:2:1793361600000
local currentLiked, versionText =
        string.match(currentState, '^([01]):(%d+):%d+$')
local countText = redis.call('GET', countKey)

if not currentLiked or not isUnsignedInteger(countText) then
    return {-4}
end

local currentCount = tonumber(countText)
local currentVersion = tonumber(versionText)

if currentCount > MAX_COUNT or currentVersion > MAX_VERSION then
    return {-5}
end

-- 5. 已经是目标状态，直接返回，不重复计数
if currentLiked == targetLiked then
    return {
        0,
        tonumber(currentLiked),
        currentCount,
        currentVersion
    }
end
-- 6. 根据实际状态变化计算新总数
local delta = targetLiked == '1' and 1 or -1
local newCount = currentCount + delta

if newCount < 0 or newCount > MAX_COUNT then
    return {-5}
end

if currentVersion >= MAX_VERSION then
    return {-5}
end

local newVersion = currentVersion + 1

-- 7. 组装新状态：点赞状态:版本号:操作时间
local newState = targetLiked
        .. ':' .. string.format('%.0f', newVersion)
        .. ':' .. occurredAt

-- 8. 更新用户状态、总数和待发送记录
redis.call('HSET', stateKey, userId, newState)

redis.call(
        'SET',
        countKey,
        string.format('%.0f', newCount)
)

redis.call('HSET', outboxKey, userId, newState)

-- 9. 返回处理结果
return {
    1,
    tonumber(targetLiked),
    newCount,
    newVersion
}
```

### 4.3 按执行顺序理解

1. **校验请求参数。** 用户 ID 必须为正数，目标状态只能是 `0/1`，操作时间必须为正整数且不超过脚本限制。
2. **校验三个 Key 的类型。** 类型不匹配返回 `{-3}`。
3. **判断是否准备好。** 状态 Key 或计数 Key 不存在，返回 `{-2}`；两者存在但当前用户字段不存在时，正常缺数据也返回 `{-2}`。
4. **检查用户状态和待发送记录。** 当前用户状态缺失但其待发送记录仍在，或两份已有记录不一致，返回 `{-4}`，避免直接覆盖异常数据。
5. **解析当前状态和计数。** `string.match` 从 `"0:2:1793361600000"` 提取状态 `0` 和版本 `2`；从计数 Key 读取共享总数。
6. **检查数值范围。** 总数上限是 Java `int` 的 `2147483647`；版本上限是 `9007199254740991`，避免超出 Lua 数值可精确表示的整数范围。
7. **已经是目标状态则直接返回。** 返回成功码 `0` 和当前数据，不更新计数、版本、时间，也不新增待发送记录；原有待发送记录保持不变。
8. **计算有效变更。** 未点赞变点赞，总数加 1；已点赞变取消，总数减 1。变更后的总数不能为负或越界，版本也不能继续越界增长。
9. **生成新版本与状态。** 状态真正改变时，版本加 1，时间使用本次传入值。
10. **执行三个写入。** 更新用户状态、共享总数、该用户的最新待发送记录，然后返回成功码 `1` 及新数据。

`string.format('%.0f', ...)` 将新版本和计数格式化成没有小数位的字符串，再保存到 Redis。

### 4.4 为什么三次写入放在同一个 Lua 中

例如一次有效点赞要完成：

```lua
redis.call('HSET', stateKey, userId, newState)
redis.call('SET', countKey, string.format('%.0f', newCount))
redis.call('HSET', outboxKey, userId, newState)
```

在同一个 Redis 实例执行该 Lua 期间，其他请求的 Redis 命令不会插入这三次写入中间。另一个点赞脚本执行时，能基于前一个脚本完成后的状态继续计算，避免两个请求都读取旧计数再相互覆盖。

**Lua 原子执行不等于执行错误会自动回滚。** 脚本提前进行类型、状态和范围检查，是为了在修改前发现常见错误；不能据此声称任何故障下三次写入都一定成功。

### 4.5 返回值

set 返回 Lua 数组，Java 使用 `DefaultRedisScript<List>` 接收。

成功时固定四项：

```text
{处理码, 当前用户点赞状态, 当前笔记总数, 当前用户状态版本}

{1, 1, 21, 1}  → 本次改为已点赞，总数21，版本1
{0, 1, 21, 1}  → 原本已点赞，本次重复请求，无需修改
{1, 0, 20, 2}  → 本次取消点赞，总数20，版本2
```

| 首项处理码 | 含义 | 返回形态 |
| --- | --- | --- |
| `1` | 状态发生变化，已更新 Redis | `{1, 状态, 总数, 版本}` |
| `0` | 已经是目标状态，无需重复修改 | `{0, 状态, 总数, 版本}` |
| `-1` | 输入参数不合法 | `{-1}` |
| `-2` | 状态 Key、计数 Key 或当前用户状态尚未准备好 | `{-2}` |
| `-3` | Redis Key 类型错误 | `{-3}` |
| `-4` | 状态格式、计数字符串或状态与待发送记录异常 | `{-4}` |
| `-5` | 计数或版本越界，或本次计算会使计数为负 | `{-5}` |

初始化的 `-2` 指检测到了不完整缓存；set 的 `-2` 指数据未就绪。Java 可能先因 set 的 `-2` 发起初始化，再被 init 的 `-2` 拒绝，不会无条件用数据库值修复所有缺失。

### 4.6 Java 如何传入参数

```java
List<?> result = stringRedisTemplate.execute(
        SET_SCRIPT,
        List.of(
                BlogLikeRedisKeys.state(blogId),
                BlogLikeRedisKeys.count(blogId),
                BlogLikeRedisKeys.outbox(blogId)
        ),
        userId.toString(),
        liked ? "1" : "0",
        Long.toString(System.currentTimeMillis())
);
```

这里的 `liked` 是请求指定的目标状态。Lua 返回的四项数据最后被转换为 `BlogLikeDTO(likedByMe, liked)`，其中 `liked` 是点赞总数，版本号不直接返回给前端。

## 5. 一次完整操作举例

假设笔记 10 的数据库总数为 20，用户 1001 没有点赞关系，Redis 三个 Key 都尚未创建。

### 5.1 第一次请求点赞

```text
用户请求 liked=true
    ↓
Java 执行 set 脚本
    ↓
状态和计数不存在，返回 {-2}
    ↓
Java initialize() 查询数据库
    总数20、用户未点赞、版本0、时间0
    ↓
执行 init 脚本，返回1
    state:{10}[1001] = "0:0:0"
    count:{10} = "20"
    outbox 尚未写入
    ↓
Java 重试一次 set 脚本
    原状态0，目标1，发生有效变更
    state:{10}[1001] = "1:1:1793361600000"
    count:{10} = "21"
    outbox:{10}[1001] = "1:1:1793361600000"
    返回 {1, 1, 21, 1}
```

初始化只负责装入原始状态；真正把总数从 20 改为 21 的是 set。

### 5.2 相同请求再次到达

```text
用户再次请求 liked=true
    ↓
set 发现 currentLiked == targetLiked
    ↓
返回 {0, 1, 21, 1}

计数仍是21，版本仍是1，时间和已有Outbox记录不变。
```

相同目标重复执行不会重复加 1，这就是当前 Redis 写入的幂等处理。

### 5.3 之后取消点赞

```text
用户请求 liked=false
    ↓
set 发现原状态1，目标0
    ↓
state:{10}[1001] = "0:2:1793361601000"
count:{10} = "20"
outbox:{10}[1001] = "0:2:1793361601000"
返回 {1, 0, 20, 2}
```

Outbox 中该用户的旧版本被最新版本替换。后续投递和消费逻辑需要围绕“最新状态 + 版本”设计；当前内容服务尚未实现这部分 MQ 链路。

## 6. 初始化时别人来点赞，会不会覆盖计数

正常的并发按需加载场景：

```text
A：从数据库读取到总数20，尚未执行自己的初始化Lua
B：完成初始化，并点赞成功，Redis总数变成21
A：开始执行初始化Lua
    → count 已存在，因此不执行 SET countKey 20
    → 只补自己尚不存在的用户字段
A：再执行真正的点赞Lua
    → 基于Redis当前总数21计算，得到22
```

能避免覆盖的原因有两个：**Lua 执行期间不能插入其他 Redis 操作；初始化逻辑还明确要求存在的数据不覆盖。** 如果在 Lua 里无条件写数据库旧计数，依然可能覆盖已经完成的变更。

两个 Lua 是两次独立调用。Java 查数据库、执行 init、再执行 set，并没有组成一个跨 Redis / MySQL 的整体原子事务，其他请求可以在这些阶段之间执行。

同一个用户重复初始化时，也会保留已有字段。对于正常状态下同一用户重复点赞，后执行的 set 读取到已点赞状态，按返回码 `0` 处理。

## 7. 当前实现的边界

- **普通缺数据和故障恢复不同。** 三个 Key 全不存在时，init 无法分辨“第一次使用”和“Redis 丢失了尚未同步的变更”。不能把它当成完整恢复方案。
- **当前防护不覆盖所有部分丢失组合。** 例如共享状态和计数仍在，但单个用户字段丢失，仍需结合待发送记录和恢复策略处理；不能宣称初始化脚本已经能修复全部异常。
- **初始化与 set 的校验范围不同。** init 主要校验输入格式和 Key 完整性；set 才执行代码中的计数、版本上限检查，不能把 set 的所有限制都理解成 init 已经检查过。
- **Lua 不负责持久化或发送 MQ。** 写入 Outbox 只表示 Redis 中保留了待发送记录，不表示 RabbitMQ 或 MySQL 已经收到。
- **查询允许短暂不一致。** 当前 GET 使用普通 HGET 与 GET，不是第三个查询 Lua；两个读取之间可能发生点赞操作。

## 8. 对应源码

- [初始化 Lua](../M1kasa-backend/m1kasa-content-service/src/main/resources/lua/blog_like_init.lua)
- [点赞 Lua](../M1kasa-backend/m1kasa-content-service/src/main/resources/lua/blog_like_set.lua)
- [Redis Key 定义](../M1kasa-backend/m1kasa-content-service/src/main/java/com/m1kasa/m1kasacontentservice/constant/BlogLikeRedisKeys.java)
- [初始化 Java 类](../M1kasa-backend/m1kasa-content-service/src/main/java/com/m1kasa/m1kasacontentservice/cache/BlogLikeCacheInitializer.java)
- [点赞与查询 Service](../M1kasa-backend/m1kasa-content-service/src/main/java/com/m1kasa/m1kasacontentservice/service/impl/BlogLikeServiceImpl.java)
- [点赞功能实现与问答总结](点赞功能实现与问答总结-20261030.md)
