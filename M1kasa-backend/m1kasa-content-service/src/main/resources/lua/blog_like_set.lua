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
        return {-4}
    end

    return {-2}
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
